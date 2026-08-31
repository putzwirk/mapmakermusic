package com.putzwirk.mapmakermusic.network;

import net.minecraft.server.level.ServerPlayer;

public final class ForgeMusicRemote implements MusicRemote {

	@Override
	public void playMusic(ServerPlayer player, String name, int volume) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.PlayMusicPacket(name, volume));
	}

	@Override
	public void stopMusic(ServerPlayer player) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.StopMusicPacket());
	}

	@Override
	public void playSound(ServerPlayer player, String name, int volume) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.PlaySoundPacket(name, volume));
	}

	@Override
	public void stopSound(ServerPlayer player) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.StopSoundPacket());
	}

	@Override
	public void stopAll(ServerPlayer player) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.StopAllPacket());
	}

	@Override
	public void reload(ServerPlayer player) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.ReloadPacket());
	}
}