package com.putzwirk.mapmakermusic.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.putzwirk.mapmakermusic.block.MusicQueue;
import org.junit.jupiter.api.Test;

public class LibraryFormatTest {

	@Test
	public void formatSeconds() {
		assertEquals("0:00", MusicLibrary.formatDuration(0f));
		assertEquals("0:05", MusicLibrary.formatDuration(5f));
		assertEquals("1:01", MusicLibrary.formatDuration(61.4f));
		assertEquals("9:59", MusicLibrary.formatDuration(599f));
	}

	@Test
	public void formatHours() {
		assertEquals("1:00:00", MusicLibrary.formatDuration(3600f));
		assertEquals("9:59:59", MusicLibrary.formatDuration(35999f));
	}

	@Test
	public void formatUnknown() {
		assertEquals("--:--", MusicLibrary.formatDuration(-1f));
	}

	@Test
	public void stopIsAlwaysAllowed() {
		assertNull(MusicLibrary.playlistBlockReason("STOP"));
		assertNull(MusicLibrary.playlistBlockReason("stop"));
	}

	@Test
	public void bounds() {
		assertEquals(20L * 1024L * 1024L, MusicLibrary.MAX_TRACK_BYTES);
		assertEquals(9f * 3600f + 59f * 60f + 59f, MusicLibrary.MAX_TRACK_SECONDS, 0.001f);
		assertEquals(MusicQueue.PlaylistItem.STOP_TRACK, "STOP");
	}
}
