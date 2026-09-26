package com.putzwirk.mapmakermusic.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class UpdateMusicBlockPacket {
	public final BlockPos pos;
	public final CompoundTag data;

	public UpdateMusicBlockPacket(BlockPos pos, CompoundTag data) {
		this.pos = pos;
		this.data = data;
	}
}
