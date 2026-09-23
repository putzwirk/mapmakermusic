package com.putzwirk.mapmakermusic.block;

import com.putzwirk.mapmakermusic.network.MusicRemotes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

public class MusicBlock extends Block implements EntityBlock {

	public MusicBlock() {
		super(BlockBehaviour.Properties.of()
				.mapColor(MapColor.WOOD)
				.strength(2.0f, 6.0f)
				.sound(SoundType.WOOD));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MusicBlockEntity(ModBlocks.MUSIC_BLOCK_ENTITY_TYPE.get(), pos, state);
	}

	public static void openConfigScreen(ServerPlayer player, MusicBlockEntity blockEntity) {
		MusicRemotes.getRemote().openMusicScreen(player, blockEntity.getBlockPos(), blockEntity.getUpdateTag());
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!level.isClientSide) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof MusicBlockEntity musicBe && player instanceof ServerPlayer serverPlayer) {
				openConfigScreen(serverPlayer, musicBe);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		if (!level.isClientSide) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof MusicBlockEntity musicBe) {
				boolean hasSignal = level.hasNeighborSignal(pos);
				if (musicBe.getActivationType() == MusicBlockEntity.ActivationType.REDSTONE) {
					boolean wasPowered = musicBe.isPoweredLastTick();
					if (hasSignal && !wasPowered) {
						MusicBlockTicker.triggerRedstoneActivation(level, musicBe);
					}
				}
				musicBe.setPoweredLastTick(hasSignal);
			}
		}
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		if (level.isClientSide) {
			return null;
		}
		return (l, p, s, be) -> {
			if (be instanceof MusicBlockEntity musicBe) {
				MusicBlockTicker.tick(l, p, s, musicBe);
			}
		};
	}
}
