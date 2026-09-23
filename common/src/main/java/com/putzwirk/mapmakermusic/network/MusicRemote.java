package com.putzwirk.mapmakermusic.network;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public interface MusicRemote {

	MusicRemote NOOP = new MusicRemote() {
		@Override
		public void playMusic(ServerPlayer player, String name, int volume, float pitch, boolean fadeIn, boolean fadeOut, Vec3 position, float maxDistance, boolean restart) {
		}

		@Override
		public void stopMusic(ServerPlayer player, boolean fadeOut) {
		}

		@Override
		public void playSound(ServerPlayer player, String name, int volume, float pitch, Vec3 position, float maxDistance) {
		}

		@Override
		public void stopSound(ServerPlayer player) {
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
	};

	void playMusic(ServerPlayer player, String name, int volume, float pitch, boolean fadeIn, boolean fadeOut, Vec3 position, float maxDistance, boolean restart);

	void stopMusic(ServerPlayer player, boolean fadeOut);

	void playSound(ServerPlayer player, String name, int volume, float pitch, Vec3 position, float maxDistance);

	void stopSound(ServerPlayer player);

	void stopAll(ServerPlayer player);

	void reload(ServerPlayer player);

	void openMusicScreen(ServerPlayer player, BlockPos pos, CompoundTag tag);

	void syncLibrary(ServerPlayer player, Map<String, Long> tracks);

	void sendTrackChunk(ServerPlayer player, String name, int totalLength, int offset, byte[] data, boolean last);
}