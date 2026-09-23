package com.putzwirk.mapmakermusic;

import com.mojang.brigadier.CommandDispatcher;
import com.putzwirk.mapmakermusic.block.AreaWandHandler;
import com.putzwirk.mapmakermusic.block.ModBlocks;
import com.putzwirk.mapmakermusic.block.MusicBlock;
import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicBlockItem;
import com.putzwirk.mapmakermusic.block.MusicBlockTicker;
import com.putzwirk.mapmakermusic.client.MapMakerMusicClient;
import com.putzwirk.mapmakermusic.command.MusicCommand;
import com.putzwirk.mapmakermusic.library.MusicLibrary;
import com.putzwirk.mapmakermusic.network.ForgeMusicRemote;
import com.putzwirk.mapmakermusic.network.MusicNetworking;
import com.putzwirk.mapmakermusic.network.MusicRemotes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MapMakerMusic.MOD_ID)
public class MapMakerMusic {
	public static final String MOD_ID = Constants.MOD_ID;

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
	private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
	private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);

	public static final RegistryObject<Block> MUSIC_BLOCK_OBJ = BLOCKS.register("music_block", MusicBlock::new);
	public static final RegistryObject<Item> MUSIC_BLOCK_ITEM_OBJ = ITEMS.register("music_block", () -> new MusicBlockItem(MUSIC_BLOCK_OBJ.get(), new Item.Properties()));
	public static final RegistryObject<BlockEntityType<MusicBlockEntity>> MUSIC_BLOCK_ENTITY_TYPE_OBJ = BLOCK_ENTITY_TYPES.register("music_block",
			() -> BlockEntityType.Builder.of(MusicBlockEntity::new, MUSIC_BLOCK_OBJ.get()).build(null));

	public MapMakerMusic() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
		BLOCK_ENTITY_TYPES.register(modEventBus);

		ModBlocks.MUSIC_BLOCK = MUSIC_BLOCK_OBJ::get;
		ModBlocks.MUSIC_BLOCK_ITEM = MUSIC_BLOCK_ITEM_OBJ::get;
		ModBlocks.MUSIC_BLOCK_ENTITY_TYPE = MUSIC_BLOCK_ENTITY_TYPE_OBJ::get;

		modEventBus.addListener(this::buildCreativeTabs);
		modEventBus.addListener(this::setupClient);

		MusicNetworking.register();
		MusicRemotes.setRemote(new ForgeMusicRemote());
		AreaWandHandler.setSelectionSender((player, pos) -> MusicNetworking.sendToPlayer(player, new MusicNetworking.WandSelectionPacket(pos)));
		MinecraftForge.EVENT_BUS.register(this);
		LOGGER.info("MapMakerMusic initialized");
	}

	private void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
			event.accept(MUSIC_BLOCK_ITEM_OBJ);
		}
	}

	private void setupClient(final FMLClientSetupEvent event) {
		if (FMLEnvironment.dist.isClient()) {
			MapMakerMusicClient.init();
		}
	}

	@SubscribeEvent
	public void onRegisterCommands(RegisterCommandsEvent event) {
		CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
		MusicCommand.register(dispatcher);
	}

	@SubscribeEvent
	public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		if (event.getLevel().isClientSide()) {
			return;
		}
		if (event.getHand() != InteractionHand.MAIN_HAND) {
			return;
		}
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (AreaWandHandler.handleLeftClick(player, event.getPos())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
			AreaWandHandler.tickPlayerSelection(player);
		}
	}

	@SubscribeEvent
	public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			MusicRemotes.getRemote().syncLibrary(player, MusicLibrary.scanTrackSizes());
		}
	}

	@SubscribeEvent
	public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			AreaWandHandler.forgetPlayer(player.getUUID());
			MusicBlockTicker.forgetPlayer(player.getUUID());
		}
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
