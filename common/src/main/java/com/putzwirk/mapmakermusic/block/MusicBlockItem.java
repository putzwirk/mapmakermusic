package com.putzwirk.mapmakermusic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class MusicBlockItem extends BlockItem {

	public MusicBlockItem(Block block, Item.Properties properties) {
		super(block, properties);
	}

	@Override
	public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
		if (player.getAbilities().instabuild) {
			return false;
		}
		return super.canAttackBlock(state, level, pos, player);
	}
}
