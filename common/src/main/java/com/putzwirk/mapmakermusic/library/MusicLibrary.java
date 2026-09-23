package com.putzwirk.mapmakermusic.library;

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

	private static volatile Map<String, Long> serverManifest = null;

	private MusicLibrary() {
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

	public static List<String> scanTrackNames() {
		Map<String, Long> manifest = serverManifest;
		if (manifest != null) {
			return Collections.unmodifiableList(new ArrayList<>(manifest.keySet()));
		}
		return Collections.unmodifiableList(new ArrayList<>(scanTracks().keySet()));
	}
}
