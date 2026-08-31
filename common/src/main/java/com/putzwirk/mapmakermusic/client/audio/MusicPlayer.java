package com.putzwirk.mapmakermusic.client.audio;

import com.putzwirk.mapmakermusic.library.MusicLibrary;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MusicPlayer {
	private static final Logger LOGGER = LoggerFactory.getLogger("MapMakerMusic Audio");
	private static final int FADE_TICKS = 40;
	private static final float RESUME_MARGIN_SECONDS = 0.25f;
	private static final float RESUME_MIN_SECONDS = 0.05f;
	private static final String STATE_FILE = "state.properties";
	private static final String KEY_TRACK = "desiredTrackKey";
	private static final String KEY_VOLUME = "desiredVolumePercent";
	private static final String KEY_POSITION = "desiredPosition";

	private final Map<String, Path> musicCache = new ConcurrentHashMap<>();
	private final Map<String, Float> lastPositions = new ConcurrentHashMap<>();
	private final List<Voice> activeSounds = new ArrayList<>();

	private Voice currentMusic;
	private Voice previousMusic;
	private Path currentMusicPath;
	private String currentTrackKey;

	private String desiredTrackKey;
	private int desiredVolumePercent = 100;

	private boolean isLoadingTrack = false;
	private long activeAlContext;

	public void init() {
		rescanMusicFolder();
		loadStateFromDisk();
		this.activeAlContext = ALC10.alcGetCurrentContext();
	}

	public void rescanMusicFolder() {
		this.musicCache.clear();
		this.musicCache.putAll(MusicLibrary.scanTracks());
		notifyPlayer("Rescanned custom music folder. Found " + this.musicCache.size() + " tracks.");
	}

	public void playMusic(String rawName, int volumePercent) {
		float volumeMultiplier = clampVolume(volumePercent);
		String key = normalizeName(rawName);
		Path path = this.musicCache.get(key);

		if (path == null) {
			LOGGER.warn("Custom music not found: {}", rawName);
			notifyPlayer("⚠ Custom music not found: " + rawName);
			return;
		}

		this.desiredTrackKey = key;
		this.desiredVolumePercent = volumePercent;

		boolean sameTrackAlreadyLive = key.equals(this.currentTrackKey) && (isPlaying(this.currentMusic) || this.isLoadingTrack);
		if (sameTrackAlreadyLive) {
			if (this.currentMusic != null) {
				this.currentMusic.volumeMultiplier = volumeMultiplier;
			}
			return;
		}

		if (this.currentTrackKey != null && this.currentMusic != null) {
			savePositionOf(this.currentTrackKey, this.currentMusic);
		}

		this.lastPositions.remove(key);

		startTrack(path, key, volumeMultiplier, 0f);
	}

	public void playSound(String rawName, int volumePercent) {
		float volumeMultiplier = clampVolume(volumePercent);
		String key = normalizeName(rawName);
		Path path = this.musicCache.get(key);

		if (path == null) {
			LOGGER.warn("Custom sound not found: {}", rawName);
			notifyPlayer("⚠ Custom sound not found: " + rawName);
			return;
		}

		CompletableFuture.supplyAsync(() -> decodeOrNull(path))
				.thenAcceptAsync(data -> {
					if (data == null) {
						return;
					}

					int buffer = AL10.alGenBuffers();
					AL10.alBufferData(buffer, data.alFormat, data.pcm, data.sampleRate);

					int source = AL10.alGenSources();
					AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
					AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
					AL10.alSource3f(source, AL10.AL_VELOCITY, 0f, 0f, 0f);
					AL10.alSourcei(source, AL10.AL_BUFFER, buffer);

					float initialGain = masterVolume() * volumeMultiplier;
					AL10.alSourcef(source, AL10.AL_GAIN, initialGain);
					AL10.alSourcePlay(source);

					this.activeSounds.add(new Voice(source, buffer, false, volumeMultiplier));
				}, Minecraft.getInstance());
	}

	public void stopMusic() {
		this.isLoadingTrack = false;
		if (this.currentMusic != null) {
			forceStop(this.currentMusic);
			this.currentMusic = null;
		}
		if (this.previousMusic != null) {
			forceStop(this.previousMusic);
			this.previousMusic = null;
		}
		if (this.currentTrackKey != null) {
			this.lastPositions.remove(this.currentTrackKey);
		}
		this.currentMusicPath = null;
		this.currentTrackKey = null;
		this.desiredTrackKey = null;
	}

	public void stopSounds() {
		for (Voice voice : this.activeSounds) {
			forceStop(voice);
		}
		this.activeSounds.clear();
	}

	public void stopAll() {
		stopMusic();
		stopSounds();
	}

	public void pauseForSessionEnd() {
		if (this.currentMusic != null && this.currentTrackKey != null) {
			savePositionOf(this.currentTrackKey, this.currentMusic);
		}
		saveStateToDisk();
		if (this.currentMusic != null) {
			forceStop(this.currentMusic);
			this.currentMusic = null;
		}
		if (this.previousMusic != null) {
			forceStop(this.previousMusic);
			this.previousMusic = null;
		}
		this.currentMusicPath = null;
		this.currentTrackKey = null;
		this.isLoadingTrack = false;
		stopSounds();
	}

	public void resumeDesiredMusic() {
		if (this.desiredTrackKey != null && !this.isLoadingTrack && isInWorld()) {
			float resumeOffset = this.lastPositions.getOrDefault(this.desiredTrackKey, 0f);
			Path path = this.musicCache.get(this.desiredTrackKey);
			if (path != null) {
				float volumeMultiplier = clampVolume(this.desiredVolumePercent);
				startTrack(path, this.desiredTrackKey, volumeMultiplier, resumeOffset);
			}
		}
	}

	public void onResourceReload() {
		if (this.currentMusic != null && this.currentTrackKey != null) {
			savePositionOf(this.currentTrackKey, this.currentMusic);
		}
	}

	public void tick() {
		long context = ALC10.alcGetCurrentContext();
		if (context != this.activeAlContext) {
			handleContextChange(context);
			return;
		}

		float baseMaster = masterVolume();

		if (this.previousMusic != null) {
			this.previousMusic.ticksElapsed++;
			float t = Math.min(1f, this.previousMusic.ticksElapsed / (float) FADE_TICKS);
			float gain = baseMaster * this.previousMusic.volumeMultiplier * (1f - t);
			AL10.alSourcef(this.previousMusic.source, AL10.AL_GAIN, gain);
			if (t >= 1f) {
				forceStop(this.previousMusic);
				this.previousMusic = null;
			}
		}

		if (this.currentMusic != null) {
			if (!isPlaying(this.currentMusic)) {
				recoverInterruptedMusic();
			} else {
				savePositionOf(this.currentTrackKey, this.currentMusic);
				if (this.currentMusic.fadeIn) {
					this.currentMusic.ticksElapsed++;
					float t = Math.min(1f, this.currentMusic.ticksElapsed / (float) FADE_TICKS);
					float gain = baseMaster * this.currentMusic.volumeMultiplier * t;
					AL10.alSourcef(this.currentMusic.source, AL10.AL_GAIN, gain);
					if (t >= 1f) {
						this.currentMusic.fadeIn = false;
					}
				} else {
					AL10.alSourcef(this.currentMusic.source, AL10.AL_GAIN, baseMaster * this.currentMusic.volumeMultiplier);
				}
			}
		} else if (this.desiredTrackKey != null && !this.isLoadingTrack && isInWorld()) {
			resumeDesiredMusic();
		}

		Iterator<Voice> soundIterator = this.activeSounds.iterator();
		while (soundIterator.hasNext()) {
			Voice sound = soundIterator.next();
			if (!isPlaying(sound)) {
				soundIterator.remove();
			} else {
				AL10.alSourcef(sound.source, AL10.AL_GAIN, baseMaster * sound.volumeMultiplier);
			}
		}
	}

	public void shutdown() {
		if (this.currentMusic != null && this.currentTrackKey != null) {
			savePositionOf(this.currentTrackKey, this.currentMusic);
		}
		saveStateToDisk();
		stopAll();
	}

	private void handleContextChange(long newContext) {
		this.activeAlContext = newContext;
		this.currentMusic = null;
		this.previousMusic = null;
		this.isLoadingTrack = false;
		this.activeSounds.clear();

		if (this.desiredTrackKey != null) {
			resumeDesiredMusic();
		}
	}

	private void recoverInterruptedMusic() {
		if (this.currentTrackKey == null || this.currentMusicPath == null || this.isLoadingTrack) {
			this.currentMusic = null;
			return;
		}

		String key = this.currentTrackKey;
		Path path = this.currentMusicPath;
		float volume = this.currentMusic.volumeMultiplier;
		float offset = this.lastPositions.getOrDefault(key, 0f);

		this.currentMusic = null;

		startTrack(path, key, volume, offset);
	}

	private void startTrack(Path path, String key, float volumeMultiplier, float resumeOffsetSeconds) {
		this.isLoadingTrack = true;
		this.currentTrackKey = key;
		this.currentMusicPath = path;

		CompletableFuture.supplyAsync(() -> decodeOrNull(path))
				.thenAcceptAsync(data -> {
					if (data == null || !key.equals(this.desiredTrackKey)) {
						this.isLoadingTrack = false;
						return;
					}

					if (this.currentMusic != null) {
						forceStop(this.currentMusic);
						this.currentMusic = null;
					}
					if (this.previousMusic != null) {
						forceStop(this.previousMusic);
						this.previousMusic = null;
					}

					int buffer = AL10.alGenBuffers();
					AL10.alBufferData(buffer, data.alFormat, data.pcm, data.sampleRate);

					int source = AL10.alGenSources();
					AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
					AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
					AL10.alSource3f(source, AL10.AL_VELOCITY, 0f, 0f, 0f);
					AL10.alSourcei(source, AL10.AL_BUFFER, buffer);

					float initialGain = masterVolume() * volumeMultiplier;
					AL10.alSourcef(source, AL10.AL_GAIN, initialGain);
					AL10.alSourcePlay(source);

					float duration = trackDurationSeconds(data);
					float safeOffset = duration > 0f
							? Math.max(0f, Math.min(resumeOffsetSeconds, Math.max(0f, duration - RESUME_MARGIN_SECONDS)))
							: 0f;
					if (safeOffset > RESUME_MIN_SECONDS) {
						AL11.alSourcef(source, AL11.AL_SEC_OFFSET, safeOffset);
					}

					this.currentMusic = new Voice(source, buffer, false, volumeMultiplier);
					this.isLoadingTrack = false;
				}, Minecraft.getInstance());
	}

	private OggDecoder.OggData decodeOrNull(Path path) {
		try {
			return OggDecoder.decode(path);
		} catch (IOException e) {
			LOGGER.warn("Failed to decode '{}': {}", path, e.getMessage());
			return null;
		}
	}

	private void savePositionOf(String key, Voice voice) {
		if (key == null || voice == null || voice.source == 0 || !isPlaying(voice)) {
			return;
		}
		float offset = AL11.alGetSourcef(voice.source, AL11.AL_SEC_OFFSET);
		if (offset >= 0f) {
			this.lastPositions.put(key, offset);
		}
	}

	private void saveStateToDisk() {
		Path file = MusicLibrary.getMusicDir().resolve(STATE_FILE);
		Properties props = new Properties();
		if (this.desiredTrackKey != null) {
			float pos = this.lastPositions.getOrDefault(this.desiredTrackKey, 0f);
			props.setProperty(KEY_TRACK, this.desiredTrackKey);
			props.setProperty(KEY_VOLUME, String.valueOf(this.desiredVolumePercent));
			props.setProperty(KEY_POSITION, String.format(Locale.ROOT, "%.3f", pos));
		}
		try (Writer writer = Files.newBufferedWriter(file)) {
			props.store(writer, null);
		} catch (IOException e) {
			LOGGER.warn("Failed to save audio state: {}", e.getMessage());
		}
	}

	private void loadStateFromDisk() {
		Path file = MusicLibrary.getMusicDir().resolve(STATE_FILE);
		if (!Files.exists(file)) {
			return;
		}
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(file)) {
			props.load(reader);
		} catch (IOException e) {
			LOGGER.warn("Failed to load audio state: {}", e.getMessage());
			return;
		}
		String track = props.getProperty(KEY_TRACK);
		if (track == null || track.isEmpty() || !this.musicCache.containsKey(track)) {
			return;
		}
		this.desiredTrackKey = track;
		this.desiredVolumePercent = Math.max(0, Math.min(100, parseInt(props.getProperty(KEY_VOLUME), this.desiredVolumePercent)));
		try {
			float pos = Math.max(0f, Float.parseFloat(props.getProperty(KEY_POSITION, "0")));
			this.lastPositions.put(track, pos);
		} catch (NumberFormatException ignored) {
		}
	}

	private int parseInt(String value, int fallback) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private float trackDurationSeconds(OggDecoder.OggData data) {
		int channels = data.alFormat == AL10.AL_FORMAT_MONO16 ? 1 : 2;
		int totalSamples = data.pcm.remaining();
		if (channels <= 0 || data.sampleRate <= 0) {
			return 0f;
		}
		int frames = totalSamples / channels;
		return frames / (float) data.sampleRate;
	}

	private boolean isPlaying(Voice voice) {
		if (voice == null || voice.source == 0) {
			return false;
		}
		return AL10.alGetSourcei(voice.source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
	}

	private void forceStop(Voice voice) {
		if (voice != null && voice.source != 0) {
			AL10.alSourceStop(voice.source);
			AL10.alDeleteSources(voice.source);
			AL10.alDeleteBuffers(voice.buffer);
		}
	}

	private float masterVolume() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.options == null) {
			return 1f;
		}
		return client.options.getSoundSourceVolume(SoundSource.MASTER);
	}

	private void notifyPlayer(String message) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.player != null) {
			client.player.displayClientMessage(Component.literal(message), true);
		}
	}

	private float clampVolume(int volumePercent) {
		return Math.max(0f, Math.min(100f, volumePercent)) / 100f;
	}

	private String normalizeName(String rawName) {
		String wanted = rawName.trim();
		if (wanted.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
			wanted = wanted.substring(0, wanted.length() - 4);
		}
		return wanted.toLowerCase(Locale.ROOT);
	}

	private static final class Voice {
		final int source;
		final int buffer;
		float volumeMultiplier;
		int ticksElapsed;
		boolean fadeIn;

		Voice(int source, int buffer, boolean fadeIn, float volumeMultiplier) {
			this.source = source;
			this.buffer = buffer;
			this.ticksElapsed = 0;
			this.fadeIn = fadeIn;
			this.volumeMultiplier = volumeMultiplier;
		}
	}

	private boolean isInWorld() {
		Minecraft client = Minecraft.getInstance();
		return client != null && client.level != null && client.player != null;
	}
}
