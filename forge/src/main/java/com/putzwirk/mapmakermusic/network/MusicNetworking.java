package com.putzwirk.mapmakermusic.network;

import com.putzwirk.mapmakermusic.MapMakerMusic;
import com.putzwirk.mapmakermusic.client.MapMakerMusicClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class MusicNetworking {
	private static final String PROTOCOL_VERSION = "6";
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
		CHANNEL.registerMessage(id++, ForgeUpdateMusicBlockPacket.class, ForgeUpdateMusicBlockPacket::encode, ForgeUpdateMusicBlockPacket::new, ForgeUpdateMusicBlockPacket::handle);
		CHANNEL.registerMessage(id++, WandSelectionPacket.class, WandSelectionPacket::encode, WandSelectionPacket::new, WandSelectionPacket::handle);
	}

	public static void sendToPlayer(ServerPlayer player, Object message) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
	}

	public static void sendToServer(Object message) {
		CHANNEL.sendToServer(message);
	}

	private static void runOnMainThread(NetworkEvent.Context context, Runnable action) {
		context.enqueueWork(action);
		context.setPacketHandled(true);
	}

	public static class PlayMusicPacket {
		public final String name;
		public final int volume;
		public final float pitch;
		public final boolean fadeIn;
		public final boolean fadeOut;
		public final Vec3 position;
		public final float maxDistance;

		public PlayMusicPacket(String name, int volume, float pitch, boolean fadeIn, boolean fadeOut, Vec3 position, float maxDistance) {
			this.name = name;
			this.volume = volume;
			this.pitch = pitch;
			this.fadeIn = fadeIn;
			this.fadeOut = fadeOut;
			this.position = position;
			this.maxDistance = maxDistance;
		}

		public PlayMusicPacket(FriendlyByteBuf buf) {
			this.name = buf.readUtf();
			this.volume = buf.readInt();
			this.pitch = buf.readFloat();
			this.fadeIn = buf.readBoolean();
			this.fadeOut = buf.readBoolean();
			this.position = buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
			this.maxDistance = buf.readFloat();
		}

		public void encode(FriendlyByteBuf buf) {
			buf.writeUtf(this.name);
			buf.writeInt(this.volume);
			buf.writeFloat(this.pitch);
			buf.writeBoolean(this.fadeIn);
			buf.writeBoolean(this.fadeOut);
			buf.writeBoolean(this.position != null);
			if (this.position != null) {
				buf.writeDouble(this.position.x);
				buf.writeDouble(this.position.y);
				buf.writeDouble(this.position.z);
			}
			buf.writeFloat(this.maxDistance);
		}

		public static void handle(PlayMusicPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			runOnMainThread(context, () -> MapMakerMusicClient.onPlayMusic(msg.name, msg.volume, msg.pitch, msg.fadeIn, msg.fadeOut, msg.position, msg.maxDistance));
		}
	}

	public static class StopMusicPacket {
		public final boolean fadeOut;

		public StopMusicPacket(boolean fadeOut) {
			this.fadeOut = fadeOut;
		}

		public StopMusicPacket(FriendlyByteBuf buf) {
			this.fadeOut = buf.readBoolean();
		}

		public void encode(FriendlyByteBuf buf) {
			buf.writeBoolean(this.fadeOut);
		}

		public static void handle(StopMusicPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			runOnMainThread(context, () -> MapMakerMusicClient.onStopMusic(msg.fadeOut));
		}
	}

	public static class PlaySoundPacket {
		public final String name;
		public final int volume;
		public final float pitch;
		public final Vec3 position;
		public final float maxDistance;

		public PlaySoundPacket(String name, int volume, float pitch, Vec3 position, float maxDistance) {
			this.name = name;
			this.volume = volume;
			this.pitch = pitch;
			this.position = position;
			this.maxDistance = maxDistance;
		}

		public PlaySoundPacket(FriendlyByteBuf buf) {
			this.name = buf.readUtf();
			this.volume = buf.readInt();
			this.pitch = buf.readFloat();
			this.position = buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
			this.maxDistance = buf.readFloat();
		}

		public void encode(FriendlyByteBuf buf) {
			buf.writeUtf(this.name);
			buf.writeInt(this.volume);
			buf.writeFloat(this.pitch);
			buf.writeBoolean(this.position != null);
			if (this.position != null) {
				buf.writeDouble(this.position.x);
				buf.writeDouble(this.position.y);
				buf.writeDouble(this.position.z);
			}
			buf.writeFloat(this.maxDistance);
		}

		public static void handle(PlaySoundPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			runOnMainThread(context, () -> MapMakerMusicClient.onPlaySound(msg.name, msg.volume, msg.pitch, msg.position, msg.maxDistance));
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

	public static class WandSelectionPacket {
		public final BlockPos pos;

		public WandSelectionPacket(BlockPos pos) {
			this.pos = pos;
		}

		public WandSelectionPacket(FriendlyByteBuf buf) {
			this.pos = buf.readBoolean() ? buf.readBlockPos() : null;
		}

		public void encode(FriendlyByteBuf buf) {
			buf.writeBoolean(this.pos != null);
			if (this.pos != null) {
				buf.writeBlockPos(this.pos);
			}
		}

		public static void handle(WandSelectionPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			runOnMainThread(context, () -> MapMakerMusicClient.onWandSelection(msg.pos));
		}
	}

	public static class ForgeUpdateMusicBlockPacket {
		public final UpdateMusicBlockPacket packet;

		public ForgeUpdateMusicBlockPacket(UpdateMusicBlockPacket packet) {
			this.packet = packet;
		}

		public ForgeUpdateMusicBlockPacket(FriendlyByteBuf buf) {
			BlockPos pos = buf.readBlockPos();
			int activationType = buf.readInt();
			int audioType = buf.readInt();
			BlockPos pos1 = buf.readBlockPos();
			BlockPos pos2 = buf.readBlockPos();
			String audioTrack = buf.readUtf();
			int volume = buf.readInt();
			float pitch = buf.readFloat();
			boolean loop = buf.readBoolean();
			boolean persistent = buf.readBoolean();
			boolean fadeIn = buf.readBoolean();
			boolean fadeOut = buf.readBoolean();
			int playbackMode = buf.readInt();
			String listenerSelector = buf.readUtf();
			BlockPos playbackPos = buf.readBlockPos();
			int radius = buf.readInt();

			this.packet = new UpdateMusicBlockPacket(pos, activationType, audioType, pos1, pos2, audioTrack, volume, pitch, loop, persistent, fadeIn, fadeOut, playbackMode, listenerSelector, playbackPos, radius);
		}

		public void encode(FriendlyByteBuf buf) {
			buf.writeBlockPos(packet.pos);
			buf.writeInt(packet.activationType);
			buf.writeInt(packet.audioType);
			buf.writeBlockPos(packet.pos1);
			buf.writeBlockPos(packet.pos2);
			buf.writeUtf(packet.audioTrack);
			buf.writeInt(packet.volume);
			buf.writeFloat(packet.pitch);
			buf.writeBoolean(packet.loop);
			buf.writeBoolean(packet.persistent);
			buf.writeBoolean(packet.fadeIn);
			buf.writeBoolean(packet.fadeOut);
			buf.writeInt(packet.playbackMode);
			buf.writeUtf(packet.listenerSelector);
			buf.writeBlockPos(packet.playbackPos);
			buf.writeInt(packet.radius);
		}

		public static void handle(ForgeUpdateMusicBlockPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			context.enqueueWork(() -> {
				ServerPlayer player = context.getSender();
				if (player != null) {
					MusicBlockServerHandler.handleUpdate(player, msg.packet);
				}
			});
			context.setPacketHandled(true);
		}
	}
}
