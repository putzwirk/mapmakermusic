package com.putzwirk.mapmakermusic.client;

import com.putzwirk.mapmakermusic.MapMakerMusic;
import com.putzwirk.mapmakermusic.client.audio.MusicPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.phys.Vec3;

public final class MapMakerMusicClient {
	private static final MusicPlayer MUSIC_PLAYER = new MusicPlayer();

	private MapMakerMusicClient() {
	}

	public static void init() {
		MUSIC_PLAYER.init();
		MinecraftForge.EVENT_BUS.register(new MapMakerMusicClient());
	}

	public static void onPlayMusic(String name, int volume) {
		Minecraft.getInstance().execute(() -> {
			stopVanillaMusic();
			MUSIC_PLAYER.playMusic(name, volume);
		});
	}

	public static void onStopMusic() {
		Minecraft.getInstance().execute(() -> {
			stopVanillaMusic();
			MUSIC_PLAYER.stopMusic();
		});
	}

	public static void onPlaySound(String name, int volume, float pitch, Vec3 position) {
		Minecraft.getInstance().execute(() -> MUSIC_PLAYER.playSound(name, volume, pitch, position));
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

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			MUSIC_PLAYER.tick();
		}
	}

	@SubscribeEvent
	public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		MUSIC_PLAYER.pauseForSessionEnd();
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
