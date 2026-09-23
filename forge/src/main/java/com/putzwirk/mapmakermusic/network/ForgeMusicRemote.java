package com.putzwirk.mapmakermusic.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ForgeMusicRemote implements MusicRemote {

	@Override
	public void playMusic(ServerPlayer player, String name, int volume, float pitch, boolean fadeIn, boolean fadeOut, Vec3 position, float maxDistance, boolean restart) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.PlayMusicPacket(name, volume, pitch, fadeIn, fadeOut, position, maxDistance, restart));
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

	@Override
	public void openMusicScreen(ServerPlayer player, BlockPos pos, CompoundTag tag) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.OpenMusicScreenPacket(pos, tag));
	}

	@Override
	public void syncLibrary(ServerPlayer player, java.util.Map<String, Long> tracks) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.LibrarySyncPacket(tracks));
	}

	@Override
	public void sendTrackChunk(ServerPlayer player, String name, int totalLength, int offset, byte[] data, boolean last) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.TrackDataPacket(name, totalLength, offset, data, last));
	}
}