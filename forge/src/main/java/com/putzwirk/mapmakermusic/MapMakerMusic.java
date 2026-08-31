package com.putzwirk.mapmakermusic;

import com.mojang.brigadier.CommandDispatcher;
import com.putzwirk.mapmakermusic.client.MapMakerMusicClient;
import com.putzwirk.mapmakermusic.command.MusicCommand;
import com.putzwirk.mapmakermusic.network.ForgeMusicRemote;
import com.putzwirk.mapmakermusic.network.MusicNetworking;
import com.putzwirk.mapmakermusic.network.MusicRemotes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MapMakerMusic.MOD_ID)
public class MapMakerMusic {
	public static final String MOD_ID = Constants.MOD_ID;

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public MapMakerMusic() {
		MusicNetworking.register();
		MusicRemotes.setRemote(new ForgeMusicRemote());
		MinecraftForge.EVENT_BUS.register(this);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setupClient);
		LOGGER.info("MapMakerMusic initialized");
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

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
