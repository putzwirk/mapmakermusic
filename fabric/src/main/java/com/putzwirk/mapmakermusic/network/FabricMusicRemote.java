package com.putzwirk.mapmakermusic.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class FabricMusicRemote implements MusicRemote {

	@Override
	public void playMusic(ServerPlayer player, String name, int volume) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeUtf(name);
		buf.writeInt(volume);
		ServerPlayNetworking.send(player, MusicNetworking.PLAY_MUSIC, buf);
	}

	@Override
	public void stopMusic(ServerPlayer player) {
		ServerPlayNetworking.send(player, MusicNetworking.STOP_MUSIC, PacketByteBufs.create());
	}

	@Override
	public void playSound(ServerPlayer player, String name, int volume) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeUtf(name);
		buf.writeInt(volume);
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
}