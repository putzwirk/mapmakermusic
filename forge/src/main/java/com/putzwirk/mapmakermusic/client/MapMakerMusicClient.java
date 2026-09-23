package com.putzwirk.mapmakermusic.client;

import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.block.ModBlocks;
import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.client.audio.MusicPlayer;
import com.putzwirk.mapmakermusic.client.gui.MusicBlockScreen;
import com.putzwirk.mapmakermusic.client.render.AreaBoxRenderer;
import com.putzwirk.mapmakermusic.library.MusicLibrary;
import com.putzwirk.mapmakermusic.network.MusicNetworking;
import com.putzwirk.mapmakermusic.network.UpdateMusicBlockPacket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MapMakerMusicClient {
	private static final MusicPlayer MUSIC_PLAYER = new MusicPlayer();
	private static final Map<String, ByteArrayOutputStream> PENDING_DOWNLOADS = new ConcurrentHashMap<>();

	private MapMakerMusicClient() {
	}

	public static void init() {
		MUSIC_PLAYER.init();
		MusicPlayer.setTrackRequester(name -> MusicNetworking.sendToServer(new MusicNetworking.TrackRequestPacket(name)));
		MinecraftForge.EVENT_BUS.register(new MapMakerMusicClient());

		MusicBlockScreen.setPacketSender((pos, activationType, audioType, pos1, pos2, audioTrack, volume, pitch, loop, persistent, fadeIn, fadeOut, playbackMode, listenerSelector, playbackPos, radius) -> {
			UpdateMusicBlockPacket packet = new UpdateMusicBlockPacket(pos, activationType, audioType, pos1, pos2, audioTrack, volume, pitch, loop, persistent, fadeIn, fadeOut, playbackMode, listenerSelector, playbackPos, radius);
			MusicNetworking.sendToServer(new MusicNetworking.ForgeUpdateMusicBlockPacket(packet));
		});
	}

	public static void onPlayMusic(String name, int volume, float pitch, boolean fadeIn, boolean fadeOut, Vec3 position, float maxDistance, boolean restart) {
		Minecraft.getInstance().execute(() -> {
			stopVanillaMusic();
			MUSIC_PLAYER.playMusic(name, volume, pitch, fadeIn, fadeOut, position, maxDistance, restart);
		});
	}

	public static void onStopMusic(boolean fadeOut) {
		Minecraft.getInstance().execute(() -> {
			stopVanillaMusic();
			MUSIC_PLAYER.stopMusic(fadeOut);
		});
	}

	public static void onPlaySound(String name, int volume, float pitch, Vec3 position, float maxDistance) {
		Minecraft.getInstance().execute(() -> MUSIC_PLAYER.playSound(name, volume, pitch, position, maxDistance));
	}

	public static void onStopSound() {
		Minecraft.getInstance().execute(MUSIC_PLAYER::stopSounds);
	}

	public static void onStopAll() {
		Minecraft.getInstance().execute(() -> {
			stopVanillaMusic();
			MUSIC_PLAYER.stopAll();
		});
	}

	public static void onReload() {
		Minecraft.getInstance().execute(MUSIC_PLAYER::rescanMusicFolder);
	}

	public static void onWandSelection(BlockPos pos) {
		Minecraft.getInstance().execute(() -> AreaBoxRenderer.setWandSelection(pos));
	}

	public static void onLibrarySync(Map<String, Long> tracks) {
		MusicLibrary.setServerTracks(tracks);
	}

	public static void onTrackData(String name, int totalLength, byte[] data, boolean last) {
		ByteArrayOutputStream out = PENDING_DOWNLOADS.computeIfAbsent(name, k -> new ByteArrayOutputStream(Math.max(0, totalLength)));
		out.write(data, 0, data.length);
		if (last) {
			PENDING_DOWNLOADS.remove(name);
			writeTrackFile(name, out.toByteArray());
			MUSIC_PLAYER.onTrackDownloaded(name);
		}
	}

	private static void writeTrackFile(String name, byte[] data) {
		try {
			Files.write(MusicLibrary.getMusicDir().resolve(name + ".ogg"), data);
		} catch (IOException e) {
			Constants.LOG.warn("Failed to save downloaded track {}: {}", name, e.getMessage());
		}
	}

	public static void onOpenMusicScreen(BlockPos pos, CompoundTag tag) {
		Minecraft.getInstance().execute(() -> {
			Minecraft client = Minecraft.getInstance();
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
		});
	}

	@SubscribeEvent
	public void onRenderLevelStage(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		AreaBoxRenderer.render(event.getPoseStack(), event.getCamera().getPosition(), client.renderBuffers().bufferSource());
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			MUSIC_PLAYER.tick();
		}
	}

	@SubscribeEvent
	public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		MUSIC_PLAYER.pauseForSessionEnd();
		MusicLibrary.setServerTracks(null);
		PENDING_DOWNLOADS.clear();
	}

	@SubscribeEvent
	public void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
		MUSIC_PLAYER.resumeDesiredMusic();
	}

	@SubscribeEvent
	public void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
		event.registerReloadListener(new SimpleReloadListener());
	}

	private static void stopVanillaMusic() {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.getSoundManager() != null) {
			client.getSoundManager().stop();
		}
	}

	private static final class SimpleReloadListener extends SimplePreparableReloadListener<net.minecraft.util.Unit> {
		@Override
		protected net.minecraft.util.Unit prepare(net.minecraft.server.packs.resources.ResourceManager p1, net.minecraft.util.profiling.ProfilerFiller p2) {
			return net.minecraft.util.Unit.INSTANCE;
		}

		@Override
		protected void apply(net.minecraft.util.Unit p1, net.minecraft.server.packs.resources.ResourceManager p2, net.minecraft.util.profiling.ProfilerFiller p3) {
			MUSIC_PLAYER.onResourceReload();
		}
	}
}
