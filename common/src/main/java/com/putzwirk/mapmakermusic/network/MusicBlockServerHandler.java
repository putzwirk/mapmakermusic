package com.putzwirk.mapmakermusic.network;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicBlockTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MusicBlockServerHandler {

	public static void handleUpdate(ServerPlayer player, UpdateMusicBlockPacket packet) {
		if (player == null || player.level() == null || packet.data == null) return;

		BlockEntity be = player.level().getBlockEntity(packet.pos);
		if (be instanceof MusicBlockEntity musicBe) {
			musicBe.load(packet.data);
			musicBe.setChanged();
			MusicBlockTicker.invalidateArea(packet.pos.asLong());
			player.level().sendBlockUpdated(packet.pos, musicBe.getBlockState(), musicBe.getBlockState(), 3);
		}
	}
}
