package com.putzwirk.mapmakermusic.block;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrackDurations {
	private static final Logger LOGGER = LoggerFactory.getLogger("MapMakerMusic Durations");
	private static final int PROBE_BYTES = 65536;

	private record CacheEntry(long size, long modified, float seconds) {
	}

	private static final Map<Path, CacheEntry> CACHE = new ConcurrentHashMap<>();

	private TrackDurations() {
	}

	public static float seconds(Path ogg) {
		if (ogg == null) {
			return -1f;
		}
		try {
			long size = Files.size(ogg);
			long modified = Files.getLastModifiedTime(ogg).toMillis();
			CacheEntry cached = CACHE.get(ogg);
			if (cached != null && cached.size() == size && cached.modified() == modified) {
				return cached.seconds();
			}
			float probed = probe(ogg, size);
			CACHE.put(ogg, new CacheEntry(size, modified, probed));
			return probed;
		} catch (IOException e) {
			return -1f;
		}
	}

	public static void invalidate(Path ogg) {
		if (ogg != null) {
			CACHE.remove(ogg);
		}
	}

	private static float probe(Path ogg, long size) {
		if (size < 64) {
			return -1f;
		}
		try (SeekableByteChannel channel = Files.newByteChannel(ogg, StandardOpenOption.READ)) {
			int sampleRate = readSampleRate(channel, size);
			if (sampleRate <= 0) {
				return -1f;
			}
			long samples = readLastGranule(channel, size);
			if (samples <= 0) {
				return -1f;
			}
			return samples / (float) sampleRate;
		} catch (IOException e) {
			LOGGER.warn("Failed to probe duration of {}: {}", ogg, e.getMessage());
			return -1f;
		}
	}

	private static int readSampleRate(SeekableByteChannel channel, long size) throws IOException {
		int head = (int) Math.min(PROBE_BYTES, size);
		ByteBuffer buffer = ByteBuffer.allocate(head).order(ByteOrder.LITTLE_ENDIAN);
		channel.position(0);
		readFully(channel, buffer);
		buffer.flip();
		for (int i = 0; i + 30 < head; i++) {
			if (buffer.get(i) == 0x01 && buffer.get(i + 1) == 'v' && buffer.get(i + 2) == 'o'
					&& buffer.get(i + 3) == 'r' && buffer.get(i + 4) == 'b' && buffer.get(i + 5) == 'i'
					&& buffer.get(i + 6) == 's') {
				return buffer.getInt(i + 12);
			}
		}
		return -1;
	}

	private static long readLastGranule(SeekableByteChannel channel, long size) throws IOException {
		int tail = (int) Math.min(PROBE_BYTES, size);
		ByteBuffer buffer = ByteBuffer.allocate(tail).order(ByteOrder.LITTLE_ENDIAN);
		channel.position(size - tail);
		readFully(channel, buffer);
		buffer.flip();
		for (int i = tail - 27; i >= 0; i--) {
			if (buffer.get(i) == 'O' && buffer.get(i + 1) == 'g' && buffer.get(i + 2) == 'g' && buffer.get(i + 3) == 'S') {
				long granule = buffer.getLong(i + 6);
				if (granule > 0) {
					return granule;
				}
			}
		}
		return -1;
	}

	private static void readFully(SeekableByteChannel channel, ByteBuffer buffer) throws IOException {
		while (buffer.hasRemaining()) {
			if (channel.read(buffer) < 0) {
				break;
			}
		}
	}
}
