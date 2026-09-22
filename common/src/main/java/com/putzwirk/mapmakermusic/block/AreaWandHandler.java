package com.putzwirk.mapmakermusic.block;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
		Level level = player.level();
		if (level.isClientSide) {
			return false;
		}
		if (!player.getAbilities().instabuild) {
			return false;
		}
		if (!isWandInMainHand(player)) {
			return false;
		}

		if (player.isShiftKeyDown()) {
			if (POS1.remove(player.getUUID()) != null) {
				notify(player, "Selection cleared.");
			} else {
				notify(player, "Nothing selected.");
			}
			sendSelection(player, null);
			return true;
		}

		BlockPos pos1 = POS1.get(player.getUUID());
		if (pos1 == null) {
			POS1.put(player.getUUID(), clickedPos);
			notify(player, "Pos1 set.");
			sendSelection(player, clickedPos);
			return true;
		}

		BlockPos pos2 = clickedPos;
		BlockPos spot = findSpot(level, player, pos2);
		if (spot == null) {
			notify(player, "No space here.");
			return true;
		}

		boolean placed = level.setBlock(spot, ModBlocks.MUSIC_BLOCK.get().defaultBlockState(), 3);
		BlockEntity be = level.getBlockEntity(spot);
		if (!placed || !(be instanceof MusicBlockEntity musicBe)) {
			notify(player, "Placement failed.");
			return true;
		}

		musicBe.setActivationType(MusicBlockEntity.ActivationType.AREA);
		musicBe.setPos1(pos1);
		musicBe.setPos2(pos2);
		musicBe.setChanged();

		POS1.remove(player.getUUID());
		sendSelection(player, null);
		notify(player, "Audiobox placed.");
		MusicBlock.openConfigScreen(player, musicBe);
		return true;
	}

	private static void sendSelection(ServerPlayer player, BlockPos pos) {
		if (selectionSender != null) {
			selectionSender.sendSelection(player, pos);
		}
	}

	private static BlockPos findSpot(Level level, ServerPlayer player, BlockPos pos2) {
		BlockPos towardsCamera = spotTowardsCamera(pos2, player.getEyePosition());
		if (towardsCamera != null && level.isEmptyBlock(towardsCamera)) {
			return towardsCamera;
		}
		BlockPos above = pos2.above();
		if (!above.equals(towardsCamera) && level.isEmptyBlock(above)) {
			return above;
		}
		for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			BlockPos side = above.relative(direction);
			if (!side.equals(towardsCamera) && level.isEmptyBlock(side)) {
				return side;
			}
		}
		BlockPos high = pos2.above(2);
		if (!high.equals(towardsCamera) && level.isEmptyBlock(high)) {
			return high;
		}
		return null;
	}

	private static BlockPos spotTowardsCamera(BlockPos pos2, Vec3 eye) {
		Vec3 center = Vec3.atCenterOf(pos2);
		double dx = eye.x - center.x;
		double dy = eye.y - center.y;
		double dz = eye.z - center.z;
		double ax = Math.abs(dx);
		double ay = Math.abs(dy);
		double az = Math.abs(dz);
		if (ax >= ay && ax >= az && ax > 1e-4) {
			return pos2.relative(dx > 0.0 ? Direction.EAST : Direction.WEST);
		}
		if (ay >= ax && ay >= az && ay > 1e-4) {
			return pos2.relative(dy > 0.0 ? Direction.UP : Direction.DOWN);
		}
		if (az > 1e-4) {
			return pos2.relative(dz > 0.0 ? Direction.SOUTH : Direction.NORTH);
		}
		return null;
	}

	private static void notify(ServerPlayer player, String message) {
		player.displayClientMessage(Component.literal(message), true);
	}
}
