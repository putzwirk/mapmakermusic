package com.putzwirk.mapmakermusic.network;

import com.putzwirk.mapmakermusic.library.MusicLibrary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class TrackTransfer {

	private static final int CHUNK_SIZE = 30000;
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "mapmakermusic-io");
		thread.setDaemon(true);
		return thread;
	});

	private TrackTransfer() {
	}

	public static void sendTrack(ServerPlayer player, String name) {
		Path path = MusicLibrary.scanTracks().get(name);
		if (path == null) {
			return;
		}

		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}

		IO.execute(() -> {
			byte[] data;
			try {
				data = Files.readAllBytes(path);
			} catch (IOException e) {
				return;
			}
			server.execute(() -> sendChunks(player, name, data));
		});
	}

	private static void sendChunks(ServerPlayer player, String name, byte[] data) {
		int total = data.length;
		if (total == 0) {
			MusicRemotes.getRemote().sendTrackChunk(player, name, 0, 0, new byte[0], true);
			return;
		}

		int offset = 0;
		while (offset < total) {
			int length = Math.min(CHUNK_SIZE, total - offset);
			boolean last = offset + length >= total;
			MusicRemotes.getRemote().sendTrackChunk(player, name, total, offset, Arrays.copyOfRange(data, offset, offset + length), last);
			offset += length;
		}
	}
}
