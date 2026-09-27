package com.putzwirk.mapmakermusic.network;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public interface MusicRemote {

	MusicRemote NOOP = new MusicRemote() {
		@Override
		public void playMusic(ServerPlayer player, String name, int volume, float pitch, boolean fadeIn, boolean fadeOut, Vec3 position, float maxDistance, boolean restart, boolean loop, float startOffsetSeconds) {
		}

		@Override
		public void stopMusic(ServerPlayer player, boolean fadeOut) {
		}

		@Override
		public void playSound(ServerPlayer player, String name, int volume, float pitch, Vec3 position, float maxDistance, boolean fadeIn) {
		}

		@Override
		public void stopSound(ServerPlayer player, boolean fadeOut) {
		}

		@Override
		public void stopAll(ServerPlayer player) {
		}

		@Override
		public void reload(ServerPlayer player) {
		}

		@Override
		public void openMusicScreen(ServerPlayer player, BlockPos pos, CompoundTag tag) {
		}

		@Override
		public void syncLibrary(ServerPlayer player, Map<String, Long> tracks) {
		}

		@Override
		public void sendTrackChunk(ServerPlayer player, String name, int totalLength, int offset, byte[] data, boolean last) {
		}

		@Override
		public void sendTestResult(ServerPlayer player, String command, boolean pass) {
		}

		@Override
		public void sendCommandSuggestions(ServerPlayer player, String command, int start, int headLen, java.util.List<String> suggestions) {
		}
	};

	void playMusic(ServerPlayer player, String name, int volume, float pitch, boolean fadeIn, boolean fadeOut, Vec3 position, float maxDistance, boolean restart, boolean loop, float startOffsetSeconds);

	void stopMusic(ServerPlayer player, boolean fadeOut);

	void playSound(ServerPlayer player, String name, int volume, float pitch, Vec3 position, float maxDistance, boolean fadeIn);

	void stopSound(ServerPlayer player, boolean fadeOut);

	void stopAll(ServerPlayer player);

	void reload(ServerPlayer player);

	void openMusicScreen(ServerPlayer player, BlockPos pos, CompoundTag tag);

	void syncLibrary(ServerPlayer player, Map<String, Long> tracks);

	void sendTrackChunk(ServerPlayer player, String name, int totalLength, int offset, byte[] data, boolean last);

	void sendTestResult(ServerPlayer player, String command, boolean pass);

	void sendCommandSuggestions(ServerPlayer player, String command, int start, int headLen, java.util.List<String> suggestions);
}