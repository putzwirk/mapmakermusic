package com.putzwirk.mapmakermusic;

import com.putzwirk.mapmakermusic.command.MusicCommand;
import com.putzwirk.mapmakermusic.network.FabricMusicRemote;
import com.putzwirk.mapmakermusic.network.MusicRemotes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapMakerMusic implements ModInitializer {
	public static final String MOD_ID = Constants.MOD_ID;

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MusicCommand.register(dispatcher));
		MusicRemotes.setRemote(new FabricMusicRemote());
		LOGGER.info("MapMakerMusic initialized");
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
