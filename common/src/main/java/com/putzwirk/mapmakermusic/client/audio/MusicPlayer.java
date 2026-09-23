package com.putzwirk.mapmakermusic.client.audio;

import com.putzwirk.mapmakermusic.library.MusicLibrary;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MusicPlayer {
	private static final Logger LOGGER = LoggerFactory.getLogger("MapMakerMusic Audio");
	private static final int FADE_TICKS = 40;
	private static final float POSITIONAL_RANGE = 16f;
	private static final float RESUME_MARGIN_SECONDS = 0.25f;
	private static final float RESUME_MIN_SECONDS = 0.05f;
	private static final String STATE_FILE = "state.properties";
	private static final String SUFFIX_TRACK = ".track";
	private static final String SUFFIX_VOLUME = ".volume";
	private static final String SUFFIX_PITCH = ".pitch";
	private static final String SUFFIX_POS_X = ".posX";
	private static final String SUFFIX_POS_Y = ".posY";
	private static final String SUFFIX_POS_Z = ".posZ";
	private static final String SUFFIX_RANGE = ".range";
	private static final String SUFFIX_POSITION = ".position";

	public interface TrackRequester {
		void requestTrack(String name);
	}

	private static volatile TrackRequester trackRequester;

	public static void setTrackRequester(TrackRequester requester) {
		trackRequester = requester;
	}

	private Runnable pendingPlayback;

	private final Map<String, Path> musicCache = new ConcurrentHashMap<>();
	private final Map<String, Float> lastPositions = new ConcurrentHashMap<>();
	private final Map<String, WorldState> worldStates = new ConcurrentHashMap<>();
	private final List<Voice> activeSounds = new ArrayList<>();

	private Voice currentMusic;
	private Voice previousMusic;
	private Path currentMusicPath;
	private String currentTrackKey;

	private String desiredTrackKey;
	private int desiredVolumePercent = 100;
	private float desiredPitch = 1f;
	private Vec3 desiredPlaybackPos = null;
	private float desiredMaxDistance = POSITIONAL_RANGE;

	private boolean isLoadingTrack = false;
	private long activeAlContext;

	private String lastKnownWorldId = null;

	public void init() {
		rescanMusicFolder();
		loadStateFromDisk();
		this.activeAlContext = ALC10.alcGetCurrentContext();
	}

	public void rescanMusicFolder() {
		rescanMusicFolder(true);
	}

	private void rescanMusicFolder(boolean notify) {
		this.musicCache.clear();
		this.musicCache.putAll(MusicLibrary.scanTracks());
		if (notify) {
			notifyPlayer("Rescanned custom music folder. Found " + this.musicCache.size() + " tracks.");
		}
	}

	private Path resolveTrack(String key) {
		Path path = this.musicCache.get(key);
		if (path == null || !java.nio.file.Files.isRegularFile(path)) {
			rescanMusicFolder(false);
			path = this.musicCache.get(key);
		}
		return path;
	}

	private boolean isStale(String key, Path path) {
		Long expected = MusicLibrary.serverTrackSize(key);
		if (expected == null) {
			return false;
		}
		try {
			return Files.size(path) != expected;
		} catch (IOException e) {
			return true;
		}
	}

	private void requestTrack(String key) {
		TrackRequester requester = trackRequester;
		if (requester != null) {
			requester.requestTrack(key);
		}
	}

	public void onTrackDownloaded(String name) {
		rescanMusicFolder(false);
		Runnable pending = this.pendingPlayback;
		this.pendingPlayback = null;
		if (pending != null) {
			pending.run();
		}
	}

	public void playMusic(String rawName, int volumePercent, float pitch, boolean enableFadeIn, boolean enableFadeOut, Vec3 position, float maxDistance, boolean restart) {
		float volumeMultiplier = clampVolume(volumePercent);
		pitch = clampPitch(pitch);
		String key = normalizeName(rawName);
		Path path = resolveTrack(key);
		if (path != null && isStale(key, path)) {
			path = null;
		}

		if (path == null) {
			if (MusicLibrary.hasServerTrack(key)) {
				final String retryName = rawName;
				final int retryVolume = volumePercent;
				final float retryPitch = pitch;
				final Vec3 retryPosition = position;
				this.pendingPlayback = () -> playMusic(retryName, retryVolume, retryPitch, enableFadeIn, enableFadeOut, retryPosition, maxDistance, restart);
				requestTrack(key);
				notifyPlayer("Downloading custom music: " + rawName);
			} else {
				LOGGER.warn("Custom music not found: {}", rawName);
				notifyPlayer("⚠ Custom music not found: " + rawName);
			}
			return;
		}

		this.desiredTrackKey = key;
		this.desiredVolumePercent = volumePercent;
		this.desiredPitch = pitch;
		this.desiredPlaybackPos = position;
		this.desiredMaxDistance = maxDistance;

		String worldId = currentWorldId();
		if (worldId != null) {
			this.worldStates.put(worldId, new WorldState(key, volumePercent, pitch, position, maxDistance, 0f));
		}

		boolean sameTrackAlreadyLive = key.equals(this.currentTrackKey) && (isPlaying(this.currentMusic) || this.isLoadingTrack);
		if (sameTrackAlreadyLive && !restart) {
			if (!this.isLoadingTrack && this.currentMusic != null) {
				this.currentMusic.volumeMultiplier = volumeMultiplier;
				this.currentMusic.pitch = pitch;
				AL10.alSourcef(this.currentMusic.source, AL10.AL_PITCH, pitch);
				if (this.currentMusic.position == null && position == null) {
					return;
				}
				if (this.currentMusic.position != null && position != null) {
					applyMusicEmitter(this.currentMusic.source, position, maxDistance);
					this.currentMusic.position = position;
					this.currentMusic.maxDistance = maxDistance;
					return;
				}
			} else {
				return;
			}
		}

		if (this.currentTrackKey != null && this.currentMusic != null) {
			savePositionOf(this.currentTrackKey, this.currentMusic);
		}

		if (!enableFadeOut) {
			if (this.previousMusic != null) {
				forceStop(this.previousMusic);
				this.previousMusic = null;
			}
			if (this.currentMusic != null) {
				forceStop(this.currentMusic);
				this.currentMusic = null;
			}
		}

		float resumeOffset = restart ? 0f : this.lastPositions.getOrDefault(key, 0f);
		this.lastPositions.keySet().retainAll(Collections.singleton(key));
		if (restart) {
			this.lastPositions.remove(key);
		}

		startTrack(path, key, volumeMultiplier, pitch, position, maxDistance, resumeOffset, enableFadeIn, enableFadeOut);
	}

	public void playSound(String rawName, int volumePercent, float pitch, Vec3 position, float maxDistance) {
		float volumeMultiplier = clampVolume(volumePercent);
		String key = normalizeName(rawName);
		Path path = resolveTrack(key);
		if (path != null && isStale(key, path)) {
			path = null;
		}

		if (path == null) {
			if (MusicLibrary.hasServerTrack(key)) {
				final String retryName = rawName;
				this.pendingPlayback = () -> playSound(retryName, volumePercent, pitch, position, maxDistance);
				requestTrack(key);
				notifyPlayer("Downloading custom sound: " + rawName);
			} else {
				LOGGER.warn("Custom sound not found: {}", rawName);
				notifyPlayer("⚠ Custom sound not found: " + rawName);
			}
			return;
		}

		final Path soundPath = path;
		CompletableFuture.supplyAsync(() -> decodeOrNull(soundPath))
				.thenAcceptAsync(decoded -> {
					if (decoded == null) {
						return;
					}

					OggDecoder.OggData data = position == null ? decoded : decoded.asMono();

					int buffer = AL10.alGenBuffers();
					int source = AL10.alGenSources();
					if (buffer == 0 || source == 0) {
						LOGGER.warn("Failed to allocate OpenAL resources for sound '{}'", rawName);
						if (buffer != 0) {
							AL10.alDeleteBuffers(buffer);
						}
						if (source != 0) {
							AL10.alDeleteSources(source);
						}
						return;
					}

					AL10.alBufferData(buffer, data.alFormat, data.pcm, data.sampleRate);

					AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
					AL10.alSourcef(source, AL10.AL_PITCH, pitch);
					AL10.alSource3f(source, AL10.AL_VELOCITY, 0f, 0f, 0f);
					AL10.alSourcei(source, AL10.AL_BUFFER, buffer);

					if (position == null) {
						AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
						AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
						AL10.alSourcei(source, AL10.AL_DISTANCE_MODEL, AL10.AL_NONE);
					} else {
						AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
						AL10.alSource3f(source, AL10.AL_POSITION, (float) position.x, (float) position.y, (float) position.z);
						AL10.alSourcei(source, AL10.AL_DISTANCE_MODEL, AL11.AL_LINEAR_DISTANCE);
						AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, Math.max(1f, maxDistance));
						AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1f);
						AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 0f);
					}

					AL10.alSourcef(source, AL10.AL_GAIN, masterVolume() * volumeMultiplier);
					AL10.alSourcePlay(source);

					this.activeSounds.add(new Voice(source, buffer, false, volumeMultiplier, pitch, position, maxDistance));
				}, Minecraft.getInstance());
	}

	public void stopMusic(boolean enableFadeOut) {
		this.isLoadingTrack = false;
		if (this.currentTrackKey != null && this.currentMusic != null) {
			savePositionOf(this.currentTrackKey, this.currentMusic);
			LOGGER.warn("[MMM-DEBUG] stopMusic key={} savedOffset={}", this.currentTrackKey, this.lastPositions.getOrDefault(this.currentTrackKey, -1f));
		}
		if (this.previousMusic != null) {
			forceStop(this.previousMusic);
			this.previousMusic = null;
		}
		if (this.currentMusic != null) {
			if (enableFadeOut) {
				this.previousMusic = this.currentMusic;
				this.previousMusic.ticksElapsed = 0;
				this.previousMusic.fadeIn = false;
			} else {
				forceStop(this.currentMusic);
			}
			this.currentMusic = null;
		}
		this.currentMusicPath = null;
		this.currentTrackKey = null;
		this.desiredTrackKey = null;

		String worldId = currentWorldId();
		if (worldId != null) {
			this.worldStates.remove(worldId);
		}
	}

	public void stopMusic() {
		stopMusic(true);
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

		String worldId = this.lastKnownWorldId;
		if (worldId != null) {
			if (this.desiredTrackKey != null) {
				float pos = this.lastPositions.getOrDefault(this.desiredTrackKey, 0f);
				this.worldStates.put(worldId, new WorldState(this.desiredTrackKey, this.desiredVolumePercent, this.desiredPitch, this.desiredPlaybackPos, this.desiredMaxDistance, pos));
			} else {
				this.worldStates.remove(worldId);
			}
		}
		this.lastKnownWorldId = null;
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
		this.desiredTrackKey = null;
		this.isLoadingTrack = false;
		stopSounds();
	}

	public void resumeDesiredMusic() {
		if (this.isLoadingTrack || !isInWorld()) {
			return;
		}

		String worldId = currentWorldId();
		WorldState state = worldId != null ? this.worldStates.get(worldId) : null;
		if (state == null) {
			this.desiredTrackKey = null;
			this.desiredVolumePercent = 100;
			this.desiredPitch = 1f;
			return;
		}

		Path path = this.musicCache.get(state.trackKey);
		if (path == null) {
			rescanMusicFolder(false);
			path = this.musicCache.get(state.trackKey);
		}
		if (path == null) {
			this.worldStates.remove(worldId);
			this.desiredTrackKey = null;
			return;
		}

		this.desiredTrackKey = state.trackKey;
		this.desiredVolumePercent = state.volumePercent;
		this.desiredPitch = state.pitch;
		this.desiredPlaybackPos = state.playbackPos;
		this.desiredMaxDistance = state.maxDistance;
		this.lastPositions.put(state.trackKey, state.position);

		float volumeMultiplier = clampVolume(state.volumePercent);
		startTrack(path, state.trackKey, volumeMultiplier, state.pitch, state.playbackPos, state.maxDistance, state.position, true, true);
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

		if (isInWorld()) {
			this.lastKnownWorldId = currentWorldId();
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
				forceStop(sound);
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
		if (this.lastKnownWorldId != null && this.desiredTrackKey != null) {
			float pos = this.lastPositions.getOrDefault(this.desiredTrackKey, 0f);
			this.worldStates.put(this.lastKnownWorldId, new WorldState(this.desiredTrackKey, this.desiredVolumePercent, this.desiredPitch, this.desiredPlaybackPos, this.desiredMaxDistance, pos));
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

		resumeDesiredMusic();
	}

	private void recoverInterruptedMusic() {
		if (this.currentTrackKey == null || this.currentMusicPath == null || this.isLoadingTrack) {
			this.currentMusic = null;
			return;
		}

		String key = this.currentTrackKey;
		Path path = this.currentMusicPath;
		float volume = this.currentMusic.volumeMultiplier;
		float pitch = this.currentMusic.pitch;
		Vec3 position = this.currentMusic.position;
		float maxDistance = this.currentMusic.maxDistance;
		float offset = this.lastPositions.getOrDefault(key, 0f);

		Voice interrupted = this.currentMusic;
		this.currentMusic = null;
		forceStop(interrupted);

		startTrack(path, key, volume, pitch, position, maxDistance, offset, true, true);
	}

	private void startTrack(Path path, String key, float volumeMultiplier, float pitch, float resumeOffsetSeconds) {
		startTrack(path, key, volumeMultiplier, pitch, null, POSITIONAL_RANGE, resumeOffsetSeconds, true, true);
	}

	private void startTrack(Path path, String key, float volumeMultiplier, float pitch, Vec3 position, float maxDistance, float resumeOffsetSeconds, boolean enableFadeIn, boolean enableFadeOut) {
		this.isLoadingTrack = true;
		this.currentTrackKey = key;
		this.currentMusicPath = path;

		CompletableFuture.supplyAsync(() -> decodeOrNull(path))
				.thenAcceptAsync(decoded -> {
					if (decoded == null || !key.equals(this.desiredTrackKey)) {
						this.isLoadingTrack = false;
						return;
					}

					OggDecoder.OggData data = position == null ? decoded : decoded.asMono();

					if (this.previousMusic != null) {
						forceStop(this.previousMusic);
						this.previousMusic = null;
					}
					if (this.currentMusic != null) {
						if (enableFadeOut) {
							this.previousMusic = this.currentMusic;
							this.previousMusic.ticksElapsed = 0;
							this.previousMusic.fadeIn = false;
						} else {
							forceStop(this.currentMusic);
						}
						this.currentMusic = null;
					}

					int buffer = AL10.alGenBuffers();
					AL10.alBufferData(buffer, data.alFormat, data.pcm, data.sampleRate);

					int source = AL10.alGenSources();
					AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
					AL10.alSourcef(source, AL10.AL_PITCH, pitch);
					AL10.alSource3f(source, AL10.AL_VELOCITY, 0f, 0f, 0f);
					applyMusicEmitter(source, position, maxDistance);
					AL10.alSourcei(source, AL10.AL_BUFFER, buffer);

					float initialGain = enableFadeIn ? 0f : masterVolume() * volumeMultiplier;
					AL10.alSourcef(source, AL10.AL_GAIN, initialGain);
					AL10.alSourcePlay(source);

					float duration = trackDurationSeconds(data);
					float safeOffset = duration > 0f
							? Math.max(0f, Math.min(resumeOffsetSeconds, Math.max(0f, duration - RESUME_MARGIN_SECONDS)))
							: 0f;
					if (safeOffset > RESUME_MIN_SECONDS) {
						AL11.alSourcef(source, AL11.AL_SEC_OFFSET, safeOffset);
					}

				this.currentMusic = new Voice(source, buffer, enableFadeIn, volumeMultiplier, pitch, position, maxDistance);
				this.isLoadingTrack = false;
				}, Minecraft.getInstance());
	}

	private void applyMusicEmitter(int source, Vec3 position, float maxDistance) {
		if (position == null) {
			AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
			AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
			AL10.alSourcei(source, AL10.AL_DISTANCE_MODEL, AL10.AL_NONE);
		} else {
			AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
			AL10.alSource3f(source, AL10.AL_POSITION, (float) position.x, (float) position.y, (float) position.z);
			AL10.alSourcei(source, AL10.AL_DISTANCE_MODEL, AL11.AL_LINEAR_DISTANCE);
			AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, Math.max(1f, maxDistance));
			AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1f);
			AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 0f);
		}
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
		for (Map.Entry<String, WorldState> entry : this.worldStates.entrySet()) {
			String worldId = entry.getKey();
			WorldState state = entry.getValue();
			props.setProperty(worldId + SUFFIX_TRACK, state.trackKey);
			props.setProperty(worldId + SUFFIX_VOLUME, String.valueOf(state.volumePercent));
			props.setProperty(worldId + SUFFIX_PITCH, String.format(Locale.ROOT, "%.3f", state.pitch));
			if (state.playbackPos != null) {
				props.setProperty(worldId + SUFFIX_POS_X, String.format(Locale.ROOT, "%.3f", state.playbackPos.x));
				props.setProperty(worldId + SUFFIX_POS_Y, String.format(Locale.ROOT, "%.3f", state.playbackPos.y));
				props.setProperty(worldId + SUFFIX_POS_Z, String.format(Locale.ROOT, "%.3f", state.playbackPos.z));
			}
			props.setProperty(worldId + SUFFIX_RANGE, String.format(Locale.ROOT, "%.3f", state.maxDistance));
			props.setProperty(worldId + SUFFIX_POSITION, String.format(Locale.ROOT, "%.3f", state.position));
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

		Map<String, String> tracks = new HashMap<>();
		Map<String, String> volumes = new HashMap<>();
		Map<String, String> pitches = new HashMap<>();
		Map<String, String> posX = new HashMap<>();
		Map<String, String> posY = new HashMap<>();
		Map<String, String> posZ = new HashMap<>();
		Map<String, String> ranges = new HashMap<>();
		Map<String, String> positions = new HashMap<>();
		for (String name : props.stringPropertyNames()) {
			if (name.endsWith(SUFFIX_TRACK)) {
				tracks.put(name.substring(0, name.length() - SUFFIX_TRACK.length()), props.getProperty(name));
			} else if (name.endsWith(SUFFIX_VOLUME)) {
				volumes.put(name.substring(0, name.length() - SUFFIX_VOLUME.length()), props.getProperty(name));
			} else if (name.endsWith(SUFFIX_PITCH)) {
				pitches.put(name.substring(0, name.length() - SUFFIX_PITCH.length()), props.getProperty(name));
			} else if (name.endsWith(SUFFIX_POS_X)) {
				posX.put(name.substring(0, name.length() - SUFFIX_POS_X.length()), props.getProperty(name));
			} else if (name.endsWith(SUFFIX_POS_Y)) {
				posY.put(name.substring(0, name.length() - SUFFIX_POS_Y.length()), props.getProperty(name));
			} else if (name.endsWith(SUFFIX_POS_Z)) {
				posZ.put(name.substring(0, name.length() - SUFFIX_POS_Z.length()), props.getProperty(name));
			} else if (name.endsWith(SUFFIX_RANGE)) {
				ranges.put(name.substring(0, name.length() - SUFFIX_RANGE.length()), props.getProperty(name));
			} else if (name.endsWith(SUFFIX_POSITION)) {
				positions.put(name.substring(0, name.length() - SUFFIX_POSITION.length()), props.getProperty(name));
			}
		}

		for (Map.Entry<String, String> entry : tracks.entrySet()) {
			String worldId = entry.getKey();
			String track = entry.getValue();
			if (track == null || track.isEmpty() || !this.musicCache.containsKey(track)) {
				continue;
			}
			int volume = Math.max(0, Math.min(100, parseInt(volumes.get(worldId), 100)));
			float pitch = parsePitch(pitches.get(worldId), 1f);
			Vec3 playbackPos = parsePlaybackPos(posX.get(worldId), posY.get(worldId), posZ.get(worldId));
			float maxDistance = parseMaxDistance(ranges.get(worldId), POSITIONAL_RANGE);
			float position = 0f;
			try {
				position = Math.max(0f, Float.parseFloat(positions.getOrDefault(worldId, "0")));
			} catch (NumberFormatException ignored) {
			}
			this.worldStates.put(worldId, new WorldState(track, volume, pitch, playbackPos, maxDistance, position));
		}
	}

	private Vec3 parsePlaybackPos(String x, String y, String z) {
		if (x == null || y == null || z == null) {
			return null;
		}
		try {
			return new Vec3(Double.parseDouble(x), Double.parseDouble(y), Double.parseDouble(z));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private float parseMaxDistance(String value, float fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return Math.max(1f, Float.parseFloat(value));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private float parsePitch(String value, float fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return clampPitch(Float.parseFloat(value));
		} catch (NumberFormatException e) {
			return fallback;
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

	private float clampPitch(float pitch) {
		return Math.max(0.1f, Math.min(4f, pitch));
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
		float pitch;
		Vec3 position;
		float maxDistance;
		int ticksElapsed;
		boolean fadeIn;

		Voice(int source, int buffer, boolean fadeIn, float volumeMultiplier, float pitch, Vec3 position, float maxDistance) {
			this.source = source;
			this.buffer = buffer;
			this.ticksElapsed = 0;
			this.fadeIn = fadeIn;
			this.volumeMultiplier = volumeMultiplier;
			this.pitch = pitch;
			this.position = position;
			this.maxDistance = maxDistance;
		}
	}

	private static final class WorldState {
		final String trackKey;
		final int volumePercent;
		final float pitch;
		final Vec3 playbackPos;
		final float maxDistance;
		final float position;

		WorldState(String trackKey, int volumePercent, float pitch, Vec3 playbackPos, float maxDistance, float position) {
			this.trackKey = trackKey;
			this.volumePercent = volumePercent;
			this.pitch = pitch;
			this.playbackPos = playbackPos;
			this.maxDistance = maxDistance;
			this.position = position;
		}
	}

	private boolean isInWorld() {
		Minecraft client = Minecraft.getInstance();
		return client != null && client.level != null && client.player != null;
	}

	private String currentWorldId() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) return null;
		ServerData serverData = client.getCurrentServer();
		if (serverData != null) {
			return "mp:" + serverData.ip;
		}
		if (client.getSingleplayerServer() != null) {
			return "sp:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		}
		return null;
	}
}