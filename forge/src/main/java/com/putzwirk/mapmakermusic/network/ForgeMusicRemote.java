package com.putzwirk.mapmakermusic.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ForgeMusicRemote implements MusicRemote {

	@Override
	public void playMusic(ServerPlayer player, String name, int volume, float pitch, boolean fadeIn, boolean fadeOut, Vec3 position, float maxDistance) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.PlayMusicPacket(name, volume, pitch, fadeIn, fadeOut, position, maxDistance));
	}

	@Override
	public void stopMusic(ServerPlayer player, boolean fadeOut) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.StopMusicPacket(fadeOut));
	}

	@Override
	public void playSound(ServerPlayer player, String name, int volume, float pitch, Vec3 position, float maxDistance) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.PlaySoundPacket(name, volume, pitch, position, maxDistance));
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