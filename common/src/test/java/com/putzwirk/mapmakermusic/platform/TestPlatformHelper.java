package com.putzwirk.mapmakermusic.platform;

import com.putzwirk.mapmakermusic.platform.services.IPlatformHelper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestPlatformHelper implements IPlatformHelper {
	public static volatile Path configDir;

	@Override
	public String getPlatformName() {
		return "Test";
	}

	@Override
	public boolean isModLoaded(String modId) {
		return false;
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return true;
	}

	@Override
	public Path getConfigDir() {
		Path dir = configDir;
		if (dir == null) {
			try {
				dir = Files.createTempDirectory("mapmakermusic-test");
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			configDir = dir;
		}
		return dir;
	}
}
