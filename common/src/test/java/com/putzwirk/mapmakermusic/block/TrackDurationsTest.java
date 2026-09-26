package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TrackDurationsTest {

	@Test
	public void nullPathReturnsUnknown() {
		assertEquals(-1f, TrackDurations.seconds(null));
	}

	@Test
	public void garbageReturnsUnknown(@TempDir Path dir) throws IOException {
		Path garbage = dir.resolve("noise.ogg");
		Files.write(garbage, new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
		assertEquals(-1f, TrackDurations.seconds(garbage));
	}

	@Test
	public void missingFileReturnsUnknown(@TempDir Path dir) {
		assertEquals(-1f, TrackDurations.seconds(dir.resolve("absent.ogg")));
	}

	@Test
	public void syntheticVorbisHeaderProbes(@TempDir Path dir) throws IOException {
		Path track = dir.resolve("tone.ogg");
		Files.write(track, syntheticOgg(48000, 96000L));
		assertEquals(2.0f, TrackDurations.seconds(track), 0.001f);
	}

	@Test
	public void probeCaches(@TempDir Path dir) throws IOException {
		Path track = dir.resolve("cached.ogg");
		Files.write(track, syntheticOgg(44100, 44100L));
		assertEquals(1.0f, TrackDurations.seconds(track), 0.001f);
		assertEquals(1.0f, TrackDurations.seconds(track), 0.001f);
		TrackDurations.invalidate(track);
		assertEquals(1.0f, TrackDurations.seconds(track), 0.001f);
	}

	@Test
	public void inconsistentIdAndTailFailsSafe(@TempDir Path dir) throws IOException {
		Path track = dir.resolve("odd.ogg");
		byte[] bytes = syntheticOgg(48000, 96000L);
		for (int i = 0; i < 4; i++) {
			bytes[i] = (byte) ('X');
		}
		Files.write(track, bytes);
		assertTrue(TrackDurations.seconds(track) < 0f || TrackDurations.seconds(track) == 2.0f);
	}

	@Test
	public void finalPageOutsideProbeWindowProbesUnknown(@TempDir Path dir) throws IOException {
		Path track = dir.resolve("tailed.ogg");
		byte[] head = syntheticOgg(48000, 96000L);
		byte[] tail = new byte[70000];
		byte[] file = new byte[head.length + tail.length];
		System.arraycopy(head, 0, file, 0, head.length);
		Files.write(track, file);
		assertEquals(-1f, TrackDurations.seconds(track));
	}

	@Test
	public void coincidentalTailBytesDictateDuration(@TempDir Path dir) throws IOException {
		Path track = dir.resolve("coincidence.ogg");
		byte[] head = syntheticOgg(48000, 96000L);
		ByteBuffer fake = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
		fake.put((byte) 'O').put((byte) 'g').put((byte) 'g').put((byte) 'S');
		fake.put((byte) 0).put((byte) 2);
		fake.putLong(172800000L);
		byte[] file = new byte[head.length + 100 + fake.capacity()];
		System.arraycopy(head, 0, file, 0, head.length);
		System.arraycopy(fake.array(), 0, file, head.length + 100, fake.capacity());
		Files.write(track, file);
		assertEquals(3600.0f, TrackDurations.seconds(track), 0.001f);
	}

	private static byte[] syntheticOgg(int sampleRate, long totalSamples) {
		ByteBuffer page = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
		page.put((byte) 'O').put((byte) 'g').put((byte) 'g').put((byte) 'S');
		page.put((byte) 0).put((byte) 2);
		page.putLong(totalSamples);
		page.putInt(0x12345678);
		page.putInt(0);
		page.putInt(0);
		page.put((byte) 1).put((byte) 30);
		page.put((byte) 1);
		page.put((byte) 'v').put((byte) 'o').put((byte) 'r').put((byte) 'b').put((byte) 'i').put((byte) 's');
		page.putInt(0);
		page.put((byte) 1);
		page.putInt(sampleRate);
		page.putInt(0);
		page.putInt(0);
		byte[] out = new byte[96];
		System.arraycopy(page.array(), 0, out, 0, 96);
		return out;
	}
}
