package com.putzwirk.mapmakermusic.client;

import com.putzwirk.mapmakermusic.MapMakerMusic;
import com.putzwirk.mapmakermusic.client.audio.MusicPlayer;
import com.putzwirk.mapmakermusic.network.MusicNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class MapMakerMusicClient implements ClientModInitializer {
	private final MusicPlayer musicPlayer = new MusicPlayer();

	@Override
	public void onInitializeClient() {
		this.musicPlayer.init();

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.PLAY_MUSIC, (client, handler, buf, responseSender) -> {
			String name = buf.readUtf();
			int volume = buf.readInt();
			client.execute(() -> {
				stopVanillaMusic();
				this.musicPlayer.playMusic(name, volume);
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.STOP_MUSIC, (client, handler, buf, responseSender) -> {
			client.execute(() -> {
				stopVanillaMusic();
				this.musicPlayer.stopMusic();
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(MusicNetworking.PLAY_SOUND, (client, handler, buf, responseSender) -> {
			String name = buf.readUtf();
			int volume = buf.readInt();
			client.execute(() -> this.musicPlayer.playSound(name, volume));
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

		ClientTickEvents.END_CLIENT_TICK.register(client -> this.musicPlayer.tick());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.musicPlayer.shutdown());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> this.musicPlayer.pauseForSessionEnd());
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

	private void stopVanillaMusic() {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.getSoundManager() != null) {
			client.getSoundManager().stop();
		}
	}
}