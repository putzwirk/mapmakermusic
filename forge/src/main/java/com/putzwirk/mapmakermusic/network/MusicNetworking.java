package com.putzwirk.mapmakermusic.network;

import com.putzwirk.mapmakermusic.MapMakerMusic;
import com.putzwirk.mapmakermusic.client.MapMakerMusicClient;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MusicNetworking {
	private static final String PROTOCOL_VERSION = "1";
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			MapMakerMusic.id("main"),
			() -> PROTOCOL_VERSION,
			PROTOCOL_VERSION::equals,
			PROTOCOL_VERSION::equals);

	private MusicNetworking() {
	}

	public static void register() {
		int id = 0;
		CHANNEL.registerMessage(id++, PlayMusicPacket.class, PlayMusicPacket::encode, PlayMusicPacket::new, PlayMusicPacket::handle);
		CHANNEL.registerMessage(id++, StopMusicPacket.class, StopMusicPacket::encode, StopMusicPacket::new, StopMusicPacket::handle);
		CHANNEL.registerMessage(id++, PlaySoundPacket.class, PlaySoundPacket::encode, PlaySoundPacket::new, PlaySoundPacket::handle);
		CHANNEL.registerMessage(id++, StopSoundPacket.class, StopSoundPacket::encode, StopSoundPacket::new, StopSoundPacket::handle);
		CHANNEL.registerMessage(id++, StopAllPacket.class, StopAllPacket::encode, StopAllPacket::new, StopAllPacket::handle);
		CHANNEL.registerMessage(id++, ReloadPacket.class, ReloadPacket::encode, ReloadPacket::new, ReloadPacket::handle);
	}

	public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player, Object message) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
	}

	private static void runOnMainThread(NetworkEvent.Context context, Runnable action) {
		context.enqueueWork(action);
		context.setPacketHandled(true);
	}

	public static class PlayMusicPacket {
		public final String name;
		public final int volume;

		public PlayMusicPacket(String name, int volume) {
			this.name = name;
			this.volume = volume;
		}

		public PlayMusicPacket(FriendlyByteBuf buf) {
			this.name = buf.readUtf();
			this.volume = buf.readInt();
		}

		public void encode(FriendlyByteBuf buf) {
			buf.writeUtf(this.name);
			buf.writeInt(this.volume);
		}

		public static void handle(PlayMusicPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			runOnMainThread(context, () -> MapMakerMusicClient.onPlayMusic(msg.name, msg.volume));
		}
	}

	public static class StopMusicPacket {
		public StopMusicPacket() {
		}

		public StopMusicPacket(FriendlyByteBuf buf) {
		}

		public void encode(FriendlyByteBuf buf) {
		}

		public static void handle(StopMusicPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			runOnMainThread(context, MapMakerMusicClient::onStopMusic);
		}
	}

	public static class PlaySoundPacket {
		public final String name;
		public final int volume;

		public PlaySoundPacket(String name, int volume) {
			this.name = name;
			this.volume = volume;
		}

		public PlaySoundPacket(FriendlyByteBuf buf) {
			this.name = buf.readUtf();
			this.volume = buf.readInt();
		}

		public void encode(FriendlyByteBuf buf) {
			buf.writeUtf(this.name);
			buf.writeInt(this.volume);
		}

		public static void handle(PlaySoundPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			runOnMainThread(context, () -> MapMakerMusicClient.onPlaySound(msg.name, msg.volume));
		}
	}

	public static class StopSoundPacket {
		public StopSoundPacket() {
		}

		public StopSoundPacket(FriendlyByteBuf buf) {
		}

		public void encode(FriendlyByteBuf buf) {
		}

		public static void handle(StopSoundPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			runOnMainThread(context, MapMakerMusicClient::onStopSound);
		}
	}

	public static class StopAllPacket {
		public StopAllPacket() {
		}

		public StopAllPacket(FriendlyByteBuf buf) {
		}

		public void encode(FriendlyByteBuf buf) {
		}

		public static void handle(StopAllPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			runOnMainThread(context, MapMakerMusicClient::onStopAll);
		}
	}

	public static class ReloadPacket {
		public ReloadPacket() {
		}

		public ReloadPacket(FriendlyByteBuf buf) {
		}

		public void encode(FriendlyByteBuf buf) {
		}

		public static void handle(ReloadPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			runOnMainThread(context, MapMakerMusicClient::onReload);
		}
	}
}
