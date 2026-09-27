package com.putzwirk.mapmakermusic.block;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class AreaWandHandler {

	private static final Map<UUID, BlockPos> POS1 = new ConcurrentHashMap<>();

	public interface WandSelectionSender {
		void sendSelection(ServerPlayer player, BlockPos pos);
	}

	private static WandSelectionSender selectionSender;

	public static void setSelectionSender(WandSelectionSender sender) {
		selectionSender = sender;
	}

	private AreaWandHandler() {
	}

	public static boolean isWandInMainHand(Player player) {
		return player.getMainHandItem().is(ModBlocks.MUSIC_BLOCK_ITEM.get());
	}

	public static void tickPlayerSelection(ServerPlayer player) {
		UUID id = player.getUUID();
		if (POS1.containsKey(id) && !isWandInMainHand(player)) {
			POS1.remove(id);
			sendSelection(player, null);
			notify(player, "Selection cleared.");
		}
	}

	public static void forgetPlayer(UUID id) {
		POS1.remove(id);
	}

	public static boolean handleLeftClick(ServerPlayer player, BlockPos clickedPos) {
		if (!isWandUseValid(player)) {
			return false;
		}

		if (player.isShiftKeyDown()) {
			clearSelection(player);
		} else {
			selectPosition(player, clickedPos);
		}
		healClientPrediction(player, clickedPos);
		return true;
	}

	public static void handleWandPunch(ServerPlayer player, BlockPos pos) {
		if (!isWandUseValid(player)) {
			return;
		}
		if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 64.0) {
			return;
		}

		if (player.isShiftKeyDown()) {
			clearSelection(player);
		} else {
			selectPosition(player, pos);
		}
		healClientPrediction(player, pos);
	}

	private static boolean isWandUseValid(ServerPlayer player) {
		Level level = player.level();
		if (level.isClientSide) {
			return false;
		}
		if (!player.hasPermissions(2)) {
			return false;
		}
		return isWandInMainHand(player);
	}

	private static void clearSelection(ServerPlayer player) {
		if (POS1.remove(player.getUUID()) != null) {
			notify(player, "Selection cleared.");
		} else {
			notify(player, "Nothing selected.");
		}
		sendSelection(player, null);
	}

	private static void healClientPrediction(ServerPlayer player, BlockPos pos) {
		Level level = player.level();
		player.connection.send(new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos)));
		BlockEntity be = level.getBlockEntity(pos);
		if (be != null) {
			Packet<?> updatePacket = be.getUpdatePacket();
			if (updatePacket != null) {
				player.connection.send(updatePacket);
			}
		}
	}

	private static void selectPosition(ServerPlayer player, BlockPos pos) {
		Level level = player.level();
		BlockPos pos1 = POS1.get(player.getUUID());
		if (pos1 == null) {
			POS1.put(player.getUUID(), pos);
			notify(player, "Pos1 set.");
			sendSelection(player, pos);
			return;
		}

		BlockPos pos2 = pos;
		if (pos2.equals(pos1)) {
			return;
		}
		boolean placed = level.setBlock(pos2, ModBlocks.MUSIC_BLOCK.get().defaultBlockState(), 3);
		BlockEntity be = level.getBlockEntity(pos2);
		if (!placed || !(be instanceof MusicBlockEntity musicBe)) {
			notify(player, "Placement failed.");
			return;
		}

		musicBe.setTriggerMode(MusicBlockEntity.TriggerMode.CHAIN);
		musicBe.setAreaGate(true);
		musicBe.setPos1(pos1);
		musicBe.setPos2(pos2);
		musicBe.setChanged();
		musicBe.broadcastUpdate();

		POS1.remove(player.getUUID());
		sendSelection(player, null);
		notify(player, "Audiobox placed.");
		MusicBlock.openConfigScreen(player, musicBe);
	}

	private static void sendSelection(ServerPlayer player, BlockPos pos) {
		if (selectionSender != null) {
			selectionSender.sendSelection(player, pos);
		}
	}

	private static void notify(ServerPlayer player, String message) {
		player.displayClientMessage(Component.literal(message), true);
	}
}
