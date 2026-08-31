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
		return Collections.unmodifiableList(new ArrayList<>(scanTracks().keySet()));
	}
}