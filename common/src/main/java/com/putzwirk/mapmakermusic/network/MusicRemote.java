package com.putzwirk.mapmakermusic.network;

import net.minecraft.server.level.ServerPlayer;

public interface MusicRemote {

	MusicRemote NOOP = new MusicRemote() {
		@Override
		public void playMusic(ServerPlayer player, String name, int volume) {
		}

		@Override
		public void stopMusic(ServerPlayer player) {
		}

		@Override
		public void playSound(ServerPlayer player, String name, int volume) {
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
	};

	void playMusic(ServerPlayer player, String name, int volume);

	void stopMusic(ServerPlayer player);

	void playSound(ServerPlayer player, String name, int volume);

	void stopSound(ServerPlayer player);

	void stopAll(ServerPlayer player);

	void reload(ServerPlayer player);
}