package com.putzwirk.mapmakermusic.network;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.putzwirk.mapmakermusic.block.condition.CommandConditionKind;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
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

	public static void handleSuggestions(ServerPlayer player, String command) {
		if (player == null || player.getServer() == null || command == null) {
			return;
		}
		if (!player.hasPermissions(2)) {
			return;
		}
		MinecraftServer server = player.getServer();
		CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput().withPermission(2);
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		String input = command.startsWith("/") ? command.substring(1) : command;
		ParseResults<CommandSourceStack> parse;
		try {
			parse = dispatcher.parse(input, source);
		} catch (RuntimeException e) {
			sendSuggestions(player, command, 0, List.of());
			return;
		}
		dispatcher.getCompletionSuggestions(parse).thenAccept(suggestions -> {
			int start = suggestions.getRange().getStart();
			List<String> texts = suggestions.getList().stream().map(Suggestion::getText).limit(8).toList();
			server.execute(() -> sendSuggestions(player, command, start, texts));
		});
	}

	private static void sendSuggestions(ServerPlayer player, String command, int start, List<String> texts) {
		if (player.getServer() == null || player.getServer().getPlayerList().getPlayer(player.getUUID()) == null) {
			return;
		}
		MusicRemotes.getRemote().sendCommandSuggestions(player, command, start, texts);
	}
}
