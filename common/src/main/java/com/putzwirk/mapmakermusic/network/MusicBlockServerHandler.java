package com.putzwirk.mapmakermusic.network;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicBlockTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicBlockServerHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger("MapMakerMusic Server");
	private static final double MAX_EDIT_DIST_SQR = 64.0;

	public static void handleUpdate(ServerPlayer player, UpdateMusicBlockPacket packet) {
		if (player == null || player.level() == null || packet.data == null) return;
		if (!player.hasPermissions(2)) {
			LOGGER.warn("Rejected music block edit from {} without permission", player.getScoreboardName());
			return;
		}
		if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(packet.pos)) > MAX_EDIT_DIST_SQR) {
			LOGGER.warn("Rejected music block edit from {} out of reach", player.getScoreboardName());
			return;
		}
		if (!player.level().isLoaded(packet.pos)) return;

		BlockEntity be = player.level().getBlockEntity(packet.pos);
		if (be instanceof MusicBlockEntity musicBe) {
			musicBe.load(packet.data);
			musicBe.setChanged();
			MusicBlockTicker.invalidateBox(player.level(), packet.pos);
			player.level().sendBlockUpdated(packet.pos, musicBe.getBlockState(), musicBe.getBlockState(), 3);
		}
	}
}
