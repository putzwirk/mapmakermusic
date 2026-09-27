package com.putzwirk.mapmakermusic.network;

import com.putzwirk.mapmakermusic.block.condition.CommandConditionKind;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConditionTestHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger("MapMakerMusic Server");

	private ConditionTestHandler() {
	}

	public static void handleTest(ServerPlayer player, String command) {
		if (player == null || player.getServer() == null || command == null) {
			return;
		}
		if (!player.hasPermissions(2)) {
			LOGGER.warn("Rejected condition test from {} without permission", player.getScoreboardName());
			MusicRemotes.getRemote().sendTestResult(player, command, false);
			return;
		}
		MusicRemotes.getRemote().sendTestResult(player, command, CommandConditionKind.runNow(player, command));
	}
}
