package com.putzwirk.mapmakermusic.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ForgeMusicRemote implements MusicRemote {

	@Override
	public void playMusic(ServerPlayer player, String name, int volume, float pitch, boolean fadeIn, boolean fadeOut, Vec3 position, float maxDistance, boolean restart, boolean loop, float startOffsetSeconds) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.PlayMusicPacket(name, volume, pitch, fadeIn, fadeOut, position, maxDistance, restart, loop, startOffsetSeconds));
	}

	@Override
	public void stopMusic(ServerPlayer player, boolean fadeOut) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.StopMusicPacket(fadeOut));
	}

	@Override
	public void playSound(ServerPlayer player, String name, int volume, float pitch, Vec3 position, float maxDistance, boolean fadeIn) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.PlaySoundPacket(name, volume, pitch, position, maxDistance, fadeIn));
	}

	@Override
	public void stopSound(ServerPlayer player, boolean fadeOut) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.StopSoundPacket(fadeOut));
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

	@Override
	public void sendTestResult(ServerPlayer player, String command, boolean pass) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.TestResultPacket(command, pass));
	}

	@Override
	public void sendCommandSuggestions(ServerPlayer player, String command, int start, int headLen, java.util.List<String> suggestions) {
		MusicNetworking.sendToPlayer(player, new MusicNetworking.TestSuggestResultPacket(command, start, headLen, suggestions));
	}
}