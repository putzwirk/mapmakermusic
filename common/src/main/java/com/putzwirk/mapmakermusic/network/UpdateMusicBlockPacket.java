package com.putzwirk.mapmakermusic.network;

import net.minecraft.core.BlockPos;

public class UpdateMusicBlockPacket {
	public final BlockPos pos;
	public final int activationType; // 0 = REDSTONE, 1 = AREA
	public final int audioType; // 0 = MUSIC, 1 = SOUND
	public final BlockPos pos1;
	public final BlockPos pos2;
	public final String audioTrack;
	public final int volume;
	public final float pitch;
	public final boolean loop;
	public final boolean persistent;
	public final boolean fadeIn;
	public final boolean fadeOut;
	public final int playbackMode; // 0 = GLOBAL, 1 = POSITIONAL
	public final String listenerSelector;
	public final BlockPos playbackPos;
	public final int radius;

	public UpdateMusicBlockPacket(BlockPos pos, int activationType, int audioType, BlockPos pos1, BlockPos pos2, String audioTrack, int volume, float pitch, boolean loop, boolean persistent, boolean fadeIn, boolean fadeOut, int playbackMode, String listenerSelector, BlockPos playbackPos, int radius) {
		this.pos = pos;
		this.activationType = activationType;
		this.audioType = audioType;
		this.pos1 = pos1;
		this.pos2 = pos2;
		this.audioTrack = audioTrack;
		this.volume = volume;
		this.pitch = pitch;
		this.loop = loop;
		this.persistent = persistent;
		this.fadeIn = fadeIn;
		this.fadeOut = fadeOut;
		this.playbackMode = playbackMode;
		this.listenerSelector = listenerSelector;
		this.playbackPos = playbackPos;
		this.radius = radius;
	}
}
