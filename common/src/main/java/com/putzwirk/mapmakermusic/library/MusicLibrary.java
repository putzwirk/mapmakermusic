package com.putzwirk.mapmakermusic.library;

import com.putzwirk.mapmakermusic.block.MusicQueue;
import com.putzwirk.mapmakermusic.block.TrackDurations;
import com.putzwirk.mapmakermusic.platform.Services;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MusicLibrary {
	private static final Logger LOGGER = LoggerFactory.getLogger("MapMakerMusic Library");

	public static final float MAX_TRACK_SECONDS = 9f * 3600f + 59f * 60f + 59f;

	public record TrackInfo(Path path, long size, float durationSeconds) {
	}

	private static volatile Map<String, Long> serverManifest = null;
	private static volatile Map<String, TrackInfo> infoCache = null;
	private static volatile Map<String, Path> tracksCache = null;

	private MusicLibrary() {
	}

	public static String normalizeTrackKey(String name) {
		if (name == null) {
			return "";
		}
		String key = name.trim();
		if (key.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
			key = key.substring(0, key.length() - 4);
		}
		return key.toLowerCase(Locale.ROOT);
	}

	public static Path getMusicDir() {
		Path dir = Services.PLATFORM.getConfigDir().resolve("mapmakermusic");
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			LOGGER.warn("Failed to create music folder: {}", e.getMessage());
		}
		return dir;
	}

	public static void setServerTracks(Map<String, Long> manifest) {
		serverManifest = manifest == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(manifest));
	}

	public static boolean hasServerTrack(String key) {
		Map<String, Long> manifest = serverManifest;
		return manifest != null && manifest.containsKey(key);
	}

	public static Long serverTrackSize(String key) {
		Map<String, Long> manifest = serverManifest;
		return manifest == null ? null : manifest.get(key);
	}

	public static Map<String, Long> scanTrackSizes() {
		Map<String, Long> sizes = new LinkedHashMap<>();
		for (Map.Entry<String, Path> entry : scanTracks().entrySet()) {
			long size = 0L;
			try {
				size = Files.size(entry.getValue());
			} catch (IOException e) {
				LOGGER.warn("Failed to read size of {}: {}", entry.getValue(), e.getMessage());
			}
			sizes.put(entry.getKey(), size);
		}
		return sizes;
	}

	public static Map<String, Path> scanTracks() {
		Map<String, Path> cached = tracksCache;
		if (cached != null) {
			return new LinkedHashMap<>(cached);
		}
		Map<String, Path> fresh = scanTracksUncached();
		tracksCache = Collections.unmodifiableMap(fresh);
		return new LinkedHashMap<>(fresh);
	}

	private static Map<String, Path> scanTracksUncached() {
		Map<String, Path> tracks = new LinkedHashMap<>();
		Path dir = getMusicDir();
		if (!Files.exists(dir)) {
			return tracks;
		}

		List<Path> entries = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path p : stream) {
				entries.add(p);
			}
		} catch (IOException e) {
			LOGGER.warn("Failed to scan music folder: {}", e.getMessage());
			return tracks;
		}

		entries.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));

		for (Path p : entries) {
			if (Files.isDirectory(p)) {
				continue;
			}
			String fileName = p.getFileName().toString();
			if (fileName.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
				String base = fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);
				tracks.put(base, p);
			}
		}

		return tracks;
	}

	public static void invalidateTrackInfo() {
		infoCache = null;
	}

	public static void invalidateTracks() {
		tracksCache = null;
		invalidateTrackInfo();
	}

	public static Map<String, TrackInfo> trackInfo() {
		Map<String, TrackInfo> cached = infoCache;
		if (cached != null) {
			return cached;
		}
		Map<String, TrackInfo> info = new LinkedHashMap<>();
		for (Map.Entry<String, Path> entry : scanTracks().entrySet()) {
			long size = 0L;
			try {
				size = Files.size(entry.getValue());
			} catch (IOException e) {
				LOGGER.warn("Failed to read size of {}: {}", entry.getValue(), e.getMessage());
			}
			info.put(entry.getKey(), new TrackInfo(entry.getValue(), size, TrackDurations.seconds(entry.getValue())));
		}
		Map<String, TrackInfo> result = Collections.unmodifiableMap(info);
		infoCache = result;
		return result;
	}

	public static String playlistBlockReason(String key) {
		if (MusicQueue.PlaylistItem.isStop(key)) {
			return null;
		}
		TrackInfo info = trackInfo().get(key);
		if (info == null) {
			return "Track not found: " + key;
		}
		if (info.durationSeconds() >= 0f && info.durationSeconds() > MAX_TRACK_SECONDS) {
			return "Track too long (max 9:59:59): " + key;
		}
		return null;
	}

	public static String formatDuration(float seconds) {
		if (seconds < 0f) {
			return "--:--";
		}
		long total = Math.round(seconds);
		long hours = total / 3600L;
		long minutes = (total % 3600L) / 60L;
		long secs = total % 60L;
		if (hours > 0) {
			return hours + ":" + twoDigits(minutes) + ":" + twoDigits(secs);
		}
		return minutes + ":" + twoDigits(secs);
	}

	private static String twoDigits(long value) {
		return value < 10 ? "0" + value : String.valueOf(value);
	}

	public static List<String> scanTrackNames() {
		Map<String, Long> manifest = serverManifest;
		if (manifest != null) {
			return Collections.unmodifiableList(new ArrayList<>(manifest.keySet()));
		}
		return Collections.unmodifiableList(new ArrayList<>(scanTracks().keySet()));
	}
}
