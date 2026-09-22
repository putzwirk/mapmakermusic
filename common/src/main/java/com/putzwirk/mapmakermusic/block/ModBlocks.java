package com.putzwirk.mapmakermusic.block;

import com.putzwirk.mapmakermusic.Constants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

public class ModBlocks {
	public static Supplier<Block> MUSIC_BLOCK;
	public static Supplier<BlockEntityType<MusicBlockEntity>> MUSIC_BLOCK_ENTITY_TYPE;
	public static Supplier<Item> MUSIC_BLOCK_ITEM;
}
