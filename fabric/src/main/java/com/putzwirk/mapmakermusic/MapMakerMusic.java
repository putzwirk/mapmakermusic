package com.putzwirk.mapmakermusic;

import com.putzwirk.mapmakermusic.block.AreaWandHandler;
import com.putzwirk.mapmakermusic.block.ModBlocks;
import com.putzwirk.mapmakermusic.block.MusicBlock;
import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicBlockItem;
import com.putzwirk.mapmakermusic.command.MusicCommand;
import com.putzwirk.mapmakermusic.network.FabricMusicRemote;
import com.putzwirk.mapmakermusic.network.MusicBlockServerHandler;
import com.putzwirk.mapmakermusic.network.MusicNetworking;
import com.putzwirk.mapmakermusic.network.MusicRemotes;
import com.putzwirk.mapmakermusic.network.UpdateMusicBlockPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapMakerMusic implements ModInitializer {
	public static final String MOD_ID = Constants.MOD_ID;

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Block MUSIC_BLOCK_OBJ;
	public static Item MUSIC_BLOCK_ITEM_OBJ;
	public static BlockEntityType<MusicBlockEntity> MUSIC_BLOCK_ENTITY_TYPE_OBJ;

	@Override
	public void onInitialize() {
		MUSIC_BLOCK_OBJ = Registry.register(BuiltInRegistries.BLOCK, id("music_block"), new MusicBlock());
		MUSIC_BLOCK_ITEM_OBJ = Registry.register(BuiltInRegistries.ITEM, id("music_block"), new MusicBlockItem(MUSIC_BLOCK_OBJ, new Item.Properties()));

		MUSIC_BLOCK_ENTITY_TYPE_OBJ = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("music_block"),
				BlockEntityType.Builder.of(MusicBlockEntity::new, MUSIC_BLOCK_OBJ).build(null));

		ModBlocks.MUSIC_BLOCK = () -> MUSIC_BLOCK_OBJ;
		ModBlocks.MUSIC_BLOCK_ITEM = () -> MUSIC_BLOCK_ITEM_OBJ;
		ModBlocks.MUSIC_BLOCK_ENTITY_TYPE = () -> MUSIC_BLOCK_ENTITY_TYPE_OBJ;

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(content -> {
			content.accept(MUSIC_BLOCK_OBJ);
		});

		ServerPlayNetworking.registerGlobalReceiver(MusicNetworking.UPDATE_MUSIC_BLOCK, (server, player, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			int activationType = buf.readInt();
			int audioType = buf.readInt();
			BlockPos pos1 = buf.readBlockPos();
			BlockPos pos2 = buf.readBlockPos();
			String audioTrack = buf.readUtf();
			int volume = buf.readInt();
			float pitch = buf.readFloat();
			boolean loop = buf.readBoolean();
			boolean persistent = buf.readBoolean();
			boolean fadeIn = buf.readBoolean();
			boolean fadeOut = buf.readBoolean();
			int playbackMode = buf.readInt();
			String listenerSelector = buf.readUtf();
			BlockPos playbackPos = buf.readBlockPos();
			int radius = buf.readInt();

			UpdateMusicBlockPacket packet = new UpdateMusicBlockPacket(pos, activationType, audioType, pos1, pos2, audioTrack, volume, pitch, loop, persistent, fadeIn, fadeOut, playbackMode, listenerSelector, playbackPos, radius);
			server.execute(() -> MusicBlockServerHandler.handleUpdate(player, packet));
		});

		MusicRemotes.setRemote(new FabricMusicRemote());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MusicCommand.register(dispatcher));
		AreaWandHandler.setSelectionSender((player, pos) -> {
			FriendlyByteBuf buf = PacketByteBufs.create();
			buf.writeBoolean(pos != null);
			if (pos != null) {
				buf.writeBlockPos(pos);
			}
			ServerPlayNetworking.send(player, MusicNetworking.WAND_SELECTION, buf);
		});
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			return AreaWandHandler.handleLeftClick(serverPlayer, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				AreaWandHandler.tickPlayerSelection(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> AreaWandHandler.forgetPlayer(handler.getPlayer().getUUID()));
		ServerPlayNetworking.registerGlobalReceiver(MusicNetworking.WAND_PUNCH_BLOCK, (server, player, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			server.execute(() -> AreaWandHandler.handleWandPunch(player, pos));
		});
		LOGGER.info("MapMakerMusic initialized");
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
