package com.putzwirk.mapmakermusic.client;

import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.MapMakerMusic;
import com.putzwirk.mapmakermusic.block.AreaWandHandler;
import com.putzwirk.mapmakermusic.block.ModBlocks;
import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.client.audio.MusicPlayer;
import com.putzwirk.mapmakermusic.client.gui.MusicBlockScreen;
import com.putzwirk.mapmakermusic.client.render.AreaBoxRenderer;
import com.putzwirk.mapmakermusic.library.MusicLibrary;
import com.putzwirk.mapmakermusic.network.MusicNetworking;

import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

public class MapMakerMusicClient implements ClientModInitializer {
	private final MusicPlayer musicPlayer = new MusicPlayer();
	private final Map<String, ByteArrayOutputStream> pendingDownloads = new ConcurrentHashMap<>();

	@Override
	public void onInitializeClient() {
		this.musicPlayer.init();
		MusicPlayer.setTrackRequester(name -> {
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			buf.writeUtf(name);
			ClientPlayNetworking.send(MusicNetworking.TRACK_REQUEST, buf);
		});

		MusicBlockScreen.setPacketSender((pos, activationType, audioType, pos1, pos2, audioTrack, volume, pitch, loop, persistent, fadeIn, fadeOut, playbackMode, listenerSelector, playbackPos, radius) -> {
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			buf.writeBlockPos(pos);
			buf.writeInt(activationType);
			buf.writeInt(audioType);
			buf.writeBlockPos(pos1);
			buf.writeBlockPos(pos2);
			buf.writeUtf(audioTrack);
			buf.writeInt(volume);
			buf.writeFloat(pitch);
			buf.writeBoolean(loop);
			buf.writeBoolean(persistent);
			buf.writeBoolean(fadeIn);
			buf.writeBoolean(fadeOut);
			buf.writeInt(playbackMode);
			buf.writeUtf(listenerSelector);
			buf.writeBlockPos(playbackPos);
			buf.writeInt(radius);

			ClientPlayNetworking.send(MusicNetworking.UPDATE_MUSIC_BLOCK, buf);
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.PLAY_MUSIC, (client, handler, buf, responseSender) -> {
			String name = buf.readUtf();
			int volume = buf.readInt();
			float pitch = buf.readFloat();
			boolean fadeIn = buf.readBoolean();
			boolean fadeOut = buf.readBoolean();
			Vec3 position = buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
			float maxDistance = buf.readFloat();
			boolean restart = buf.readBoolean();
			client.execute(() -> {
				stopVanillaMusic();
				this.musicPlayer.playMusic(name, volume, pitch, fadeIn, fadeOut, position, maxDistance, restart);
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.STOP_MUSIC, (client, handler, buf, responseSender) -> {
			boolean fadeOut = buf.readBoolean();
			client.execute(() -> {
				stopVanillaMusic();
				this.musicPlayer.stopMusic(fadeOut);
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.PLAY_SOUND, (client, handler, buf, responseSender) -> {
			String name = buf.readUtf();
			int volume = buf.readInt();
			float pitch = buf.readFloat();
			Vec3 position = buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
			float maxDistance = buf.readFloat();
			client.execute(() -> this.musicPlayer.playSound(name, volume, pitch, position, maxDistance));
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.STOP_SOUND, (client, handler, buf, responseSender) -> {
			client.execute(this.musicPlayer::stopSounds);
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.STOP_ALL, (client, handler, buf, responseSender) -> {
			client.execute(() -> {
				stopVanillaMusic();
				this.musicPlayer.stopAll();
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.RELOAD, (client, handler, buf, responseSender) -> {
			client.execute(this.musicPlayer::rescanMusicFolder);
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.OPEN_MUSIC_SCREEN, (client, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			CompoundTag tag = buf.readNbt();
			client.execute(() -> openMusicScreen(client, pos, tag));
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.LIBRARY_SYNC, (client, handler, buf, responseSender) -> {
			int count = buf.readInt();
			Map<String, Long> manifest = new LinkedHashMap<>();
			for (int i = 0; i < count; i++) {
				manifest.put(buf.readUtf(), buf.readLong());
			}
			client.execute(() -> MusicLibrary.setServerTracks(manifest));
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.TRACK_DATA, (client, handler, buf, responseSender) -> {
			String name = buf.readUtf();
			int totalLength = buf.readInt();
			buf.readInt();
			byte[] data = buf.readByteArray();
			boolean last = buf.readBoolean();
			ByteArrayOutputStream out = this.pendingDownloads.computeIfAbsent(name, k -> new ByteArrayOutputStream(Math.max(0, totalLength)));
			out.write(data, 0, data.length);
			if (last) {
				this.pendingDownloads.remove(name);
				writeTrackFile(name, out.toByteArray());
				client.execute(() -> this.musicPlayer.onTrackDownloaded(name));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.WAND_SELECTION, (client, handler, buf, responseSender) -> {
			boolean hasPos = buf.readBoolean();
			net.minecraft.core.BlockPos pos = hasPos ? buf.readBlockPos() : null;
			client.execute(() -> AreaBoxRenderer.setWandSelection(pos));
		});

		WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
			if (context.consumers() == null) {
				return;
			}
			AreaBoxRenderer.render(context.matrixStack(), context.camera().getPosition(), context.consumers());
		});

		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (!world.isClientSide() || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (!player.getAbilities().instabuild || !AreaWandHandler.isWandInMainHand(player)) {
				return InteractionResult.PASS;
			}
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			buf.writeBlockPos(pos);
			ClientPlayNetworking.send(MusicNetworking.WAND_PUNCH_BLOCK, buf);
			return InteractionResult.FAIL;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> this.musicPlayer.tick());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.musicPlayer.shutdown());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			this.musicPlayer.pauseForSessionEnd();
			MusicLibrary.setServerTracks(null);
			this.pendingDownloads.clear();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> this.musicPlayer.resumeDesiredMusic());

		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return MapMakerMusic.id("music_reload_listener");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				musicPlayer.onResourceReload();
			}
		});
	}

	private static void writeTrackFile(String name, byte[] data) {
		try {
			Path target = MusicLibrary.getMusicDir().resolve(name + ".ogg");
			Files.write(target, data);
		} catch (IOException e) {
			Constants.LOG.warn("Failed to save downloaded track {}: {}", name, e.getMessage());
		}
	}

	private static void openMusicScreen(Minecraft client, BlockPos pos, CompoundTag tag) {
		if (client.level == null) {
			return;
		}
		MusicBlockEntity musicBe;
		if (client.level.getBlockEntity(pos) instanceof MusicBlockEntity existing) {
			musicBe = existing;
		} else {
			musicBe = new MusicBlockEntity(pos, ModBlocks.MUSIC_BLOCK.get().defaultBlockState());
		}
		if (tag != null) {
			musicBe.load(tag);
		}
		client.setScreen(new MusicBlockScreen(musicBe));
	}

	private void stopVanillaMusic() {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.getSoundManager() != null) {
			client.getSoundManager().stop();
		}
	}
}