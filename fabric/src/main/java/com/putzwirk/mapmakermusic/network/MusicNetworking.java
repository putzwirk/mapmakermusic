package com.putzwirk.mapmakermusic.network;

import com.putzwirk.mapmakermusic.MapMakerMusic;
import net.minecraft.resources.ResourceLocation;

public final class MusicNetworking {
	public static final ResourceLocation PLAY_MUSIC = MapMakerMusic.id("play_music");
	public static final ResourceLocation STOP_MUSIC = MapMakerMusic.id("stop_music");
	public static final ResourceLocation PLAY_SOUND = MapMakerMusic.id("play_sound");
	public static final ResourceLocation STOP_SOUND = MapMakerMusic.id("stop_sound");
	public static final ResourceLocation STOP_ALL = MapMakerMusic.id("stop_all");
	public static final ResourceLocation RELOAD = MapMakerMusic.id("reload");
	public static final ResourceLocation UPDATE_MUSIC_BLOCK = MapMakerMusic.id("update_music_block");
	public static final ResourceLocation WAND_SELECTION = MapMakerMusic.id("wand_selection");
	public static final ResourceLocation WAND_PUNCH_BLOCK = MapMakerMusic.id("wand_punch_block");

	private MusicNetworking() {
	}
}