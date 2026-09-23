package com.putzwirk.mapmakermusic.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class FabricMusicRemote implements MusicRemote {

	@Override
	public void playMusic(ServerPlayer player, String name, int volume, float pitch, boolean fadeIn, boolean fadeOut, Vec3 position, float maxDistance, boolean restart) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeUtf(name);
		buf.writeInt(volume);
		buf.writeFloat(pitch);
		buf.writeBoolean(fadeIn);
		buf.writeBoolean(fadeOut);
		buf.writeBoolean(position != null);
		if (position != null) {
			buf.writeDouble(position.x);
			buf.writeDouble(position.y);
			buf.writeDouble(position.z);
		}
		buf.writeFloat(maxDistance);
		buf.writeBoolean(restart);
		ServerPlayNetworking.send(player, MusicNetworking.PLAY_MUSIC, buf);
	}

	@Override
	public void stopMusic(ServerPlayer player, boolean fadeOut) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(fadeOut);
		ServerPlayNetworking.send(player, MusicNetworking.STOP_MUSIC, buf);
	}

	@Override
	public void playSound(ServerPlayer player, String name, int volume, float pitch, Vec3 position, float maxDistance) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeUtf(name);
		buf.writeInt(volume);
		buf.writeFloat(pitch);
		buf.writeBoolean(position != null);
		if (position != null) {
			buf.writeDouble(position.x);
			buf.writeDouble(position.y);
			buf.writeDouble(position.z);
		}
		buf.writeFloat(maxDistance);
		ServerPlayNetworking.send(player, MusicNetworking.PLAY_SOUND, buf);
	}

	@Override
	public void stopSound(ServerPlayer player) {
		ServerPlayNetworking.send(player, MusicNetworking.STOP_SOUND, PacketByteBufs.create());
	}

	@Override
	public void stopAll(ServerPlayer player) {
		ServerPlayNetworking.send(player, MusicNetworking.STOP_ALL, PacketByteBufs.create());
	}

	@Override
	public void reload(ServerPlayer player) {
		ServerPlayNetworking.send(player, MusicNetworking.RELOAD, PacketByteBufs.create());
	}

	@Override
	public void openMusicScreen(ServerPlayer player, BlockPos pos, CompoundTag tag) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeNbt(tag);
		ServerPlayNetworking.send(player, MusicNetworking.OPEN_MUSIC_SCREEN, buf);
	}

	@Override
	public void syncLibrary(ServerPlayer player, java.util.Map<String, Long> tracks) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeInt(tracks.size());
		for (java.util.Map.Entry<String, Long> entry : tracks.entrySet()) {
			buf.writeUtf(entry.getKey());
			buf.writeLong(entry.getValue());
		}
		ServerPlayNetworking.send(player, MusicNetworking.LIBRARY_SYNC, buf);
	}

	@Override
	public void sendTrackChunk(ServerPlayer player, String name, int totalLength, int offset, byte[] data, boolean last) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeUtf(name);
		buf.writeInt(totalLength);
		buf.writeInt(offset);
		buf.writeByteArray(data);
		buf.writeBoolean(last);
		ServerPlayNetworking.send(player, MusicNetworking.TRACK_DATA, buf);
	}
}