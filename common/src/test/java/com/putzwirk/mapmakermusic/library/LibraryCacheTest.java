package com.putzwirk.mapmakermusic.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.putzwirk.mapmakermusic.network.TrackRequestThrottle;
import com.putzwirk.mapmakermusic.platform.TestPlatformHelper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LibraryCacheTest {

	@Test
	public void normalizeTrackKey() {
		assertEquals("song", MusicLibrary.normalizeTrackKey("Song.OGG"));
		assertEquals("song", MusicLibrary.normalizeTrackKey("  song.ogg  "));
		assertEquals("cave", MusicLibrary.normalizeTrackKey("CAVE"));
		assertEquals("stop", MusicLibrary.normalizeTrackKey("STOP"));
		assertEquals("", MusicLibrary.normalizeTrackKey(null));
		assertEquals("", MusicLibrary.normalizeTrackKey("   "));
	}

	@Test
	public void scanCachesUntilInvalidated(@TempDir Path dir) throws Exception {
		TestPlatformHelper.configDir = dir;
		MusicLibrary.invalidateTracks();
		Path musicDir = MusicLibrary.getMusicDir();

		Files.write(musicDir.resolve("alpha.ogg"), new byte[] {1, 2, 3});
		Map<String, Path> first = MusicLibrary.scanTracks();
		assertEquals(1, first.size());
		assertTrue(first.containsKey("alpha"));

		Files.write(musicDir.resolve("beta.ogg"), new byte[] {4, 5, 6});
		Map<String, Path> second = MusicLibrary.scanTracks();
		assertEquals(1, second.size());

		MusicLibrary.invalidateTracks();
		Map<String, Path> third = MusicLibrary.scanTracks();
		assertEquals(2, third.size());
		assertTrue(third.containsKey("beta"));
		MusicLibrary.invalidateTracks();
	}

	@Test
	public void throttleLimitsRepeatRequests() {
		TrackRequestThrottle throttle = new TrackRequestThrottle();
		UUID player = UUID.randomUUID();
		UUID other = UUID.randomUUID();

		assertTrue(throttle.tryAcquire(player, 0L));
		assertFalse(throttle.tryAcquire(player, 500L));
		assertTrue(throttle.tryAcquire(other, 500L));
		assertTrue(throttle.tryAcquire(player, 1000L));
		assertFalse(throttle.tryAcquire(player, 1500L));
	}
}
