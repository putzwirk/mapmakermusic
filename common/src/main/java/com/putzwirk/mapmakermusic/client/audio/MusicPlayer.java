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
	public static final int FADE_TICKS = 40;
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

	public interface TrackFinishedCallback {
		void onTrackFinished(String trackKey);
	}

	private static volatile TrackRequester trackRequester;
	private static volatile TrackFinishedCallback trackFinishedCallback;

	public static void setTrackRequester(TrackRequester requester) {
		trackRequester = requester;
	}

	public static void setTrackFinishedCallback(TrackFinishedCallback callback) {
		trackFinishedCallback = callback;
	}

	private final Map<String, List<Runnable>> pendingPlaybacks = new ConcurrentHashMap<>();

	private static final int MAX_CACHED_BUFFERS = 3;
	private static final long MAX_CACHED_BYTES = 384L * 1024L * 1024L;

	private static final class CachedBuffer {
		final int buffer;
		final Path path;
		final long size;
		final long modified;
		final long bytes;
		final float duration;
		final int sampleRate;
		final int alFormat;

		CachedBuffer(int buffer, Path path, long size, long modified, long bytes, float duration, int sampleRate, int alFormat) {
			this.buffer = buffer;
			this.path = path;
			this.size = size;
			this.modified = modified;
			this.bytes = bytes;
			this.duration = duration;
			this.sampleRate = sampleRate;
			this.alFormat = alFormat;
		}
	}

	private final java.util.LinkedHashMap<String, CachedBuffer> bufferCache = new java.util.LinkedHashMap<>(16, 0.75f, true);
	private long cachedBytes = 0;

	private static String bufferCacheKey(String key, boolean mono) {
		return mono ? key + "|m" : key + "|s";
	}

	private CachedBuffer takeCachedBuffer(String key, boolean mono, Path path) {
		String cacheKey = bufferCacheKey(key, mono);
		CachedBuffer entry = this.bufferCache.get(cacheKey);
		if (entry == null) {
			return null;
		}
		try {
			if (!entry.path.equals(path) || Files.size(path) != entry.size
					|| Files.getLastModifiedTime(path).toMillis() != entry.modified) {
				this.bufferCache.remove(cacheKey);
				this.cachedBytes -= entry.bytes;
				deleteBufferIfFree(entry.buffer);
				return null;
			}
		} catch (IOException e) {
			this.bufferCache.remove(cacheKey);
			this.cachedBytes -= entry.bytes;
			deleteBufferIfFree(entry.buffer);
			return null;
		}
		return entry;
	}

	private void storeCachedBuffer(String key, boolean mono, Path path, OggDecoder.OggData data, int buffer, float duration) {
		long bytes = (long) data.pcm.remaining() * 2L;
		if (bytes <= 0L || bytes > MAX_CACHED_BYTES) {
			return;
		}
		long size;
		long modified;
		try {
			size = Files.size(path);
			modified = Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return;
		}
		evictCachedBuffers(bytes);
		if (this.bufferCache.size() >= MAX_CACHED_BUFFERS || this.cachedBytes + bytes > MAX_CACHED_BYTES) {
			return;
		}
		CachedBuffer old = this.bufferCache.put(bufferCacheKey(key, mono), new CachedBuffer(buffer, path, size, modified, bytes, duration, data.sampleRate, data.alFormat));
		this.cachedBytes += bytes;
		if (old != null) {
			this.cachedBytes -= old.bytes;
			deleteBufferIfFree(old.buffer);
		}
	}

	private void evictCachedBuffers(long needBytes) {
		var it = this.bufferCache.entrySet().iterator();
		while (it.hasNext() && (this.bufferCache.size() >= MAX_CACHED_BUFFERS || this.cachedBytes + needBytes > MAX_CACHED_BYTES)) {
			Map.Entry<String, CachedBuffer> eldest = it.next();
			if (isBufferReferenced(eldest.getValue().buffer)) {
				continue;
			}
			it.remove();
			this.cachedBytes -= eldest.getValue().bytes;
			AL10.alDeleteBuffers(eldest.getValue().buffer);
		}
	}

	private boolean isBufferReferenced(int buffer) {
		if (this.currentMusic != null && this.currentMusic.buffer == buffer) {
			return true;
		}
		for (Voice voice : this.fadingVoices) {
			if (voice.buffer == buffer) {
				return true;
			}
		}
		for (Voice voice : this.activeSounds) {
			if (voice.buffer == buffer) {
				return true;
			}
		}
		return false;
	}

	private boolean isBufferCached(int buffer) {
		for (CachedBuffer entry : this.bufferCache.values()) {
			if (entry.buffer == buffer) {
				return true;
			}
		}
		return false;
	}

	private void deleteBufferIfFree(int buffer) {
		if (buffer != 0 && !isBufferReferenced(buffer)) {
			AL10.alDeleteBuffers(buffer);
		}
	}

	private final Map<String, Path> musicCache = new ConcurrentHashMap<>();
	private final Map<String, Float> lastPositions = new ConcurrentHashMap<>();
	private final Map<String, WorldState> worldStates = new ConcurrentHashMap<>();
	private final List<Voice> activeSounds = new ArrayList<>();

	private Voice currentMusic;
	private final List<Voice> fadingVoices = new ArrayList<>();
	private Path currentMusicPath;
	private String currentTrackKey;

	private String desiredTrackKey;
	private int desiredVolumePercent = 100;
	private float desiredPitch = 1f;
	private Vec3 desiredPlaybackPos = null;
	private float desiredMaxDistance = POSITIONAL_RANGE;

	private boolean isLoadingTrack = false;
	private long activeAlContext;
	private long decodeEpoch = 0;
	private long lastMusicRequestMillis = 0;

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
		MusicLibrary.invalidateTrackInfo();
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
		List<Runnable> runnables = this.pendingPlaybacks.remove(normalizeName(name));
		if (runnables != null) {
			for (Runnable pending : new ArrayList<>(runnables)) {
				pending.run();
			}
		}
	}

	private void queuePending(String key, Runnable playback) {
		this.pendingPlaybacks.computeIfAbsent(key, k -> new ArrayList<>()).add(playback);
	}

	public void playMusic(String rawName, int volumePercent, float pitch, boolean enableFadeIn, boolean enableFadeOut, Vec3 position, float maxDistance, boolean restart, boolean loop) {
		playMusic(rawName, volumePercent, pitch, enableFadeIn, enableFadeOut, position, maxDistance, restart, loop, -1f);
	}

	public void playMusic(String rawName, int volumePercent, float pitch, boolean enableFadeIn, boolean enableFadeOut, Vec3 position, float maxDistance, boolean restart, boolean loop, float startOffsetSeconds) {
		if (com.putzwirk.mapmakermusic.block.MusicDebug.ENABLED) {
			LOGGER.info("[dbg] playrx track={} offset={} fadeIn={} fadeOut={} loop={} restart={}", rawName, startOffsetSeconds, enableFadeIn, enableFadeOut, loop, restart);
		}
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
				this.queuePending(key, () -> playMusic(retryName, retryVolume, retryPitch, enableFadeIn, enableFadeOut, retryPosition, maxDistance, restart, loop, startOffsetSeconds));
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
			clearFadingVoices();
			if (this.currentMusic != null) {
				forceStop(this.currentMusic);
				this.currentMusic = null;
			}
		}

		float resumeOffset = startOffsetSeconds >= 0f ? startOffsetSeconds : (restart ? 0f : this.lastPositions.getOrDefault(key, 0f));
		this.lastPositions.keySet().retainAll(Collections.singleton(key));
		if (restart) {
			this.lastPositions.remove(key);
		}

		startTrack(path, key, volumeMultiplier, pitch, position, maxDistance, resumeOffset, enableFadeIn, enableFadeOut, loop);
	}

	public void playSound(String rawName, int volumePercent, float pitch, Vec3 position, float maxDistance) {
		playSound(rawName, volumePercent, pitch, position, maxDistance, false);
	}

	public void playSound(String rawName, int volumePercent, float pitch, Vec3 position, float maxDistance, boolean fadeIn) {
		float volumeMultiplier = clampVolume(volumePercent);
		String key = normalizeName(rawName);
		Path path = resolveTrack(key);
		if (path != null && isStale(key, path)) {
			path = null;
		}

		if (path == null) {
			if (MusicLibrary.hasServerTrack(key)) {
				final String retryName = rawName;
				this.queuePending(key, () -> playSound(retryName, volumePercent, pitch, position, maxDistance, fadeIn));
				requestTrack(key);
				notifyPlayer("Downloading custom sound: " + rawName);
			} else {
				LOGGER.warn("Custom sound not found: {}", rawName);
				notifyPlayer("⚠ Custom sound not found: " + rawName);
			}
			return;
		}

		final Path soundPath = path;
		CachedBuffer cached = takeCachedBuffer(key, position != null, soundPath);
		if (cached != null) {
			fireCachedSound(cached, volumeMultiplier, pitch, position, maxDistance, fadeIn);
			return;
		}
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
						decoded.free();
						if (buffer != 0) {
							AL10.alDeleteBuffers(buffer);
						}
						if (source != 0) {
							AL10.alDeleteSources(source);
						}
						return;
					}

					AL10.alBufferData(buffer, data.alFormat, data.pcm, data.sampleRate);
					decoded.free();
					storeCachedBuffer(key, position != null, soundPath, data, buffer, 0f);

					AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
					AL10.alSourcef(source, AL10.AL_PITCH, pitch);
					AL10.alSource3f(source, AL10.AL_VELOCITY, 0f, 0f, 0f);
					AL10.alSourcei(source, AL10.AL_BUFFER, buffer);

					fireSoundSource(source, buffer, volumeMultiplier, pitch, position, maxDistance, fadeIn);
				}, Minecraft.getInstance());
	}

	private void fireCachedSound(CachedBuffer cached, float volumeMultiplier, float pitch, Vec3 position, float maxDistance, boolean fadeIn) {
		int source = AL10.alGenSources();
		if (source == 0) {
			return;
		}
		AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
		AL10.alSourcef(source, AL10.AL_PITCH, pitch);
		AL10.alSource3f(source, AL10.AL_VELOCITY, 0f, 0f, 0f);
		AL10.alSourcei(source, AL10.AL_BUFFER, cached.buffer);

		fireSoundSource(source, cached.buffer, volumeMultiplier, pitch, position, maxDistance, fadeIn);
	}

	private void fireSoundSource(int source, int buffer, float volumeMultiplier, float pitch, Vec3 position, float maxDistance, boolean fadeIn) {
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

		AL10.alSourcef(source, AL10.AL_GAIN, fadeIn ? 0f : masterVolume() * volumeMultiplier);
		AL10.alSourcePlay(source);

		this.activeSounds.add(new Voice(source, buffer, fadeIn, volumeMultiplier, pitch, position, maxDistance, false));
	}

	public void stopMusic(boolean enableFadeOut) {
		if (com.putzwirk.mapmakermusic.block.MusicDebug.ENABLED) {
			LOGGER.info("[dbg] stoprx current={} fading={} fadeOut={}", this.currentTrackKey, this.fadingVoices.size(), enableFadeOut);
		}
		this.isLoadingTrack = false;
		this.lastMusicRequestMillis = System.currentTimeMillis();
		if (this.currentTrackKey != null && this.currentMusic != null) {
			savePositionOf(this.currentTrackKey, this.currentMusic);
		}
		if (this.currentMusic != null) {
			if (enableFadeOut) {
				this.currentMusic.ticksElapsed = 0;
				this.currentMusic.fadeIn = false;
				this.currentMusic.fadeOut = true;
				this.fadingVoices.add(this.currentMusic);
			} else {
				forceStop(this.currentMusic);
			}
			this.currentMusic = null;
		} else if (!enableFadeOut) {
			clearFadingVoices();
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
		stopSounds(false);
	}

	public void stopSounds(boolean fadeOut) {
		if (!fadeOut) {
			for (Voice voice : this.activeSounds) {
				forceStop(voice);
			}
			this.activeSounds.clear();
			return;
		}
		for (Voice voice : this.activeSounds) {
			voice.fadeIn = false;
			voice.fadeOut = true;
			voice.ticksElapsed = 0;
		}
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
		clearFadingVoices();
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
		if (state.trackKey.equals(this.currentTrackKey) && (this.isLoadingTrack || this.currentMusic != null)) {
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

		if (System.currentTimeMillis() - this.lastMusicRequestMillis < 3000L) {
			return;
		}

		float volumeMultiplier = clampVolume(state.volumePercent);
		startTrack(path, state.trackKey, volumeMultiplier, state.pitch, state.playbackPos, state.maxDistance, state.position, true, true, true);
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

		Iterator<Voice> fadingIterator = this.fadingVoices.iterator();
		while (fadingIterator.hasNext()) {
			Voice fading = fadingIterator.next();
			fading.ticksElapsed++;
			float t = Math.min(1f, fading.ticksElapsed / (float) FADE_TICKS);
			if (isPlaying(fading)) {
				AL10.alSourcef(fading.source, AL10.AL_GAIN, baseMaster * fading.volumeMultiplier * (1f - t));
			}
			if (t >= 1f) {
				forceStop(fading);
				fadingIterator.remove();
			}
		}

		if (this.currentMusic != null) {
			if (!isPlaying(this.currentMusic)) {
				if (this.currentMusic.loop) {
					recoverInterruptedMusic();
				} else {
					handleFinishedMusic();
				}
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
			} else if (sound.fadeOut) {
				sound.ticksElapsed++;
				float t = Math.min(1f, sound.ticksElapsed / (float) FADE_TICKS);
				AL10.alSourcef(sound.source, AL10.AL_GAIN, baseMaster * sound.volumeMultiplier * (1f - t));
				if (t >= 1f) {
					forceStop(sound);
					soundIterator.remove();
				}
			} else if (sound.fadeIn) {
				sound.ticksElapsed++;
				float t = Math.min(1f, sound.ticksElapsed / (float) FADE_TICKS);
				AL10.alSourcef(sound.source, AL10.AL_GAIN, baseMaster * sound.volumeMultiplier * t);
				if (t >= 1f) {
					sound.fadeIn = false;
				}
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
		clearFadingVoices();
		this.isLoadingTrack = false;
		this.activeSounds.clear();
		this.bufferCache.clear();
		this.cachedBytes = 0;

		resumeDesiredMusic();
	}

	private void clearFadingVoices() {
		for (Voice voice : this.fadingVoices) {
			forceStop(voice);
		}
		this.fadingVoices.clear();
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

		startTrack(path, key, volume, pitch, position, maxDistance, offset, true, true, true);
	}

	private void handleFinishedMusic() {
		Voice finished = this.currentMusic;
		String finishedKey = this.currentTrackKey;
		if (com.putzwirk.mapmakermusic.block.MusicDebug.ENABLED) {
			LOGGER.info("[dbg] finished key={}", finishedKey);
		}
		if (finished != null) {
			forceStop(finished);
		}
		this.currentMusic = null;
		if (!this.isLoadingTrack) {
			this.currentTrackKey = null;
			this.currentMusicPath = null;
			this.desiredTrackKey = null;
			String worldId = currentWorldId();
			if (worldId != null) {
				this.worldStates.remove(worldId);
			}
		}
		TrackFinishedCallback callback = trackFinishedCallback;
		if (callback != null && finishedKey != null) {
			callback.onTrackFinished(finishedKey);
		}
	}

	private void startTrack(Path path, String key, float volumeMultiplier, float pitch, float resumeOffsetSeconds) {
		startTrack(path, key, volumeMultiplier, pitch, null, POSITIONAL_RANGE, resumeOffsetSeconds, true, true, true);
	}

	private void startTrack(Path path, String key, float volumeMultiplier, float pitch, Vec3 position, float maxDistance, float resumeOffsetSeconds, boolean enableFadeIn, boolean enableFadeOut, boolean loop) {
		this.decodeEpoch++;
		this.lastMusicRequestMillis = System.currentTimeMillis();
		CachedBuffer cached = takeCachedBuffer(key, position != null, path);
		if (cached != null) {
			retireCurrentMusic(enableFadeOut);
			playCachedMusic(cached, key, volumeMultiplier, pitch, position, maxDistance, resumeOffsetSeconds, enableFadeIn, enableFadeOut, loop);
			return;
		}
		this.isLoadingTrack = true;
		this.currentTrackKey = key;
		this.currentMusicPath = path;
		long decodeStart = System.nanoTime();
		final long epoch = this.decodeEpoch;

		CompletableFuture.supplyAsync(() -> decodeOrNull(path))
				.thenAcceptAsync(decoded -> {
					if (com.putzwirk.mapmakermusic.block.MusicDebug.ENABLED) {
						LOGGER.info("[dbg] decoded key={} ms={} ok={} desired={} epochOk={}", key,
								(System.nanoTime() - decodeStart) / 1000000L, decoded != null, this.desiredTrackKey, epoch == this.decodeEpoch);
					}
					if (decoded == null || epoch != this.decodeEpoch || !key.equals(this.desiredTrackKey)) {
						if (epoch == this.decodeEpoch) {
							this.isLoadingTrack = false;
						}
						return;
					}

					OggDecoder.OggData data = position == null ? decoded : decoded.asMono();

					retireCurrentMusic(enableFadeOut);

					int buffer = AL10.alGenBuffers();
					AL10.alBufferData(buffer, data.alFormat, data.pcm, data.sampleRate);
					decoded.free();

					int source = AL10.alGenSources();
					AL10.alSourcei(source, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
					AL10.alSourcef(source, AL10.AL_PITCH, pitch);
					AL10.alSource3f(source, AL10.AL_VELOCITY, 0f, 0f, 0f);
					applyMusicEmitter(source, position, maxDistance);
					AL10.alSourcei(source, AL10.AL_BUFFER, buffer);

					float initialGain = enableFadeIn ? 0f : masterVolume() * volumeMultiplier;
					AL10.alSourcef(source, AL10.AL_GAIN, initialGain);
					AL10.alSourcePlay(source);

					float duration = trackDurationSeconds(data);
					storeCachedBuffer(key, position != null, path, data, buffer, duration);
					float safeOffset = duration > 0f
							? Math.max(0f, Math.min(resumeOffsetSeconds, Math.max(0f, duration - RESUME_MARGIN_SECONDS)))
							: 0f;
					if (safeOffset > RESUME_MIN_SECONDS) {
						AL11.alSourcef(source, AL11.AL_SEC_OFFSET, safeOffset);
					}

				this.currentMusic = new Voice(source, buffer, enableFadeIn, volumeMultiplier, pitch, position, maxDistance, loop);
				this.isLoadingTrack = false;
				if (com.putzwirk.mapmakermusic.block.MusicDebug.ENABLED) {
					LOGGER.info("[dbg] started key={} fading={} dur={}", key, this.fadingVoices.size(), duration);
				}
				}, Minecraft.getInstance());
	}

	private void retireCurrentMusic(boolean enableFadeOut) {
		if (this.currentMusic != null) {
			if (enableFadeOut) {
				this.currentMusic.ticksElapsed = 0;
				this.currentMusic.fadeIn = false;
				this.currentMusic.fadeOut = true;
				this.fadingVoices.add(this.currentMusic);
				while (this.fadingVoices.size() > 3) {
					forceStop(this.fadingVoices.remove(0));
				}
			} else {
				forceStop(this.currentMusic);
			}
			this.currentMusic = null;
		}
	}

	private void playCachedMusic(CachedBuffer cached, String key, float volumeMultiplier, float pitch, Vec3 position, float maxDistance, float resumeOffsetSeconds, boolean enableFadeIn, boolean enableFadeOut, boolean loop) {
		this.currentTrackKey = key;
		this.currentMusicPath = cached.path;
		int source = AL10.alGenSources();
		AL10.alSourcei(source, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
		AL10.alSourcef(source, AL10.AL_PITCH, pitch);
		AL10.alSource3f(source, AL10.AL_VELOCITY, 0f, 0f, 0f);
		applyMusicEmitter(source, position, maxDistance);
		AL10.alSourcei(source, AL10.AL_BUFFER, cached.buffer);

		float initialGain = enableFadeIn ? 0f : masterVolume() * volumeMultiplier;
		AL10.alSourcef(source, AL10.AL_GAIN, initialGain);
		AL10.alSourcePlay(source);

		float safeOffset = cached.duration > 0f
				? Math.max(0f, Math.min(resumeOffsetSeconds, Math.max(0f, cached.duration - RESUME_MARGIN_SECONDS)))
				: 0f;
		if (safeOffset > RESUME_MIN_SECONDS) {
			AL11.alSourcef(source, AL11.AL_SEC_OFFSET, safeOffset);
		}

		this.currentMusic = new Voice(source, cached.buffer, enableFadeIn, volumeMultiplier, pitch, position, maxDistance, loop);
		this.isLoadingTrack = false;
		if (com.putzwirk.mapmakermusic.block.MusicDebug.ENABLED) {
			LOGGER.info("[dbg] started key={} fading={} dur={} cached=true", key, this.fadingVoices.size(), cached.duration);
		}
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
			if (!isBufferCached(voice.buffer)) {
				AL10.alDeleteBuffers(voice.buffer);
			}
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
		boolean fadeOut;
		boolean loop;

		Voice(int source, int buffer, boolean fadeIn, float volumeMultiplier, float pitch, Vec3 position, float maxDistance, boolean loop) {
			this.source = source;
			this.buffer = buffer;
			this.ticksElapsed = 0;
			this.fadeIn = fadeIn;
			this.volumeMultiplier = volumeMultiplier;
			this.pitch = pitch;
			this.position = position;
			this.maxDistance = maxDistance;
			this.loop = loop;
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