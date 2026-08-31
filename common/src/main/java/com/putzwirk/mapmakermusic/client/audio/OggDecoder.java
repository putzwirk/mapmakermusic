package com.putzwirk.mapmakermusic.client.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;

public final class OggDecoder {

	private OggDecoder() {
	}

	public static OggData decode(Path oggPath) throws IOException {
		byte[] bytes = Files.readAllBytes(oggPath);
		ByteBuffer dataBuffer = BufferUtils.createByteBuffer(bytes.length);
		dataBuffer.put(bytes);
		dataBuffer.flip();

		IntBuffer channelsBuf = BufferUtils.createIntBuffer(1);
		IntBuffer sampleRateBuf = BufferUtils.createIntBuffer(1);
		ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(dataBuffer, channelsBuf, sampleRateBuf);
		if (pcm == null) {
			throw new IOException("Failed to decode OGG file: " + oggPath);
		}

		int channels = channelsBuf.get(0);
		int sampleRate = sampleRateBuf.get(0);
		int format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
		return new OggData(pcm, format, sampleRate);
	}

	public static final class OggData {
		public final ShortBuffer pcm;
		public final int alFormat;
		public final int sampleRate;

		public OggData(ShortBuffer pcm, int alFormat, int sampleRate) {
			this.pcm = pcm;
			this.alFormat = alFormat;
			this.sampleRate = sampleRate;
		}
	}
}
