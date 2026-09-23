package com.putzwirk.mapmakermusic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.putzwirk.mapmakermusic.library.MusicLibrary;
import com.putzwirk.mapmakermusic.network.MusicRemotes;
import java.util.Collection;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class MusicCommand {

	private static final SuggestionProvider<CommandSourceStack> TRACK_SUGGESTIONS =
			(context, builder) -> SharedSuggestionProvider.suggest(MusicLibrary.scanTrackNames(), builder);

	private MusicCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("mmmusic")
				.requires(source -> source.hasPermission(2) || (source.getEntity() instanceof Player player && player.isCreative()))
				.then(Commands.literal("playmusic")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(TRACK_SUGGESTIONS)
										.executes(ctx -> executePlayMusic(ctx, 100, 1f, null, 16f))
										.then(Commands.argument("volume", IntegerArgumentType.integer(0, 100))
												.executes(ctx -> executePlayMusic(ctx, IntegerArgumentType.getInteger(ctx, "volume"), 1f, null, 16f))
												.then(Commands.argument("pitch", FloatArgumentType.floatArg(0.1f, 4f))
														.executes(ctx -> executePlayMusic(ctx, IntegerArgumentType.getInteger(ctx, "volume"), FloatArgumentType.getFloat(ctx, "pitch"), null, 16f))
														.then(Commands.argument("pos", Vec3Argument.vec3())
																.executes(ctx -> executePlayMusic(ctx, IntegerArgumentType.getInteger(ctx, "volume"), FloatArgumentType.getFloat(ctx, "pitch"), Vec3Argument.getVec3(ctx, "pos"), 16f))
																.then(Commands.argument("range", IntegerArgumentType.integer(1, 64))
																		.executes(ctx -> executePlayMusic(ctx, IntegerArgumentType.getInteger(ctx, "volume"), FloatArgumentType.getFloat(ctx, "pitch"), Vec3Argument.getVec3(ctx, "pos"), IntegerArgumentType.getInteger(ctx, "range"))))))))))
				.then(Commands.literal("stopmusic")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(MusicCommand::executeStopMusic)))
				.then(Commands.literal("playsound")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(TRACK_SUGGESTIONS)
										.executes(ctx -> executePlaySound(ctx, 100, 1f, null, 16f))
										.then(Commands.argument("volume", IntegerArgumentType.integer(0, 100))
												.executes(ctx -> executePlaySound(ctx, IntegerArgumentType.getInteger(ctx, "volume"), 1f, null, 16f))
												.then(Commands.argument("pitch", FloatArgumentType.floatArg(0.5f, 2f))
														.executes(ctx -> executePlaySound(ctx, IntegerArgumentType.getInteger(ctx, "volume"), FloatArgumentType.getFloat(ctx, "pitch"), null, 16f))
														.then(Commands.argument("pos", Vec3Argument.vec3())
																.executes(ctx -> executePlaySound(ctx, IntegerArgumentType.getInteger(ctx, "volume"), FloatArgumentType.getFloat(ctx, "pitch"), Vec3Argument.getVec3(ctx, "pos"), 16f))
																.then(Commands.argument("range", IntegerArgumentType.integer(1, 256))
																		.executes(ctx -> executePlaySound(ctx, IntegerArgumentType.getInteger(ctx, "volume"), FloatArgumentType.getFloat(ctx, "pitch"), Vec3Argument.getVec3(ctx, "pos"), IntegerArgumentType.getInteger(ctx, "range"))))))))))
				.then(Commands.literal("stopsound")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(MusicCommand::executeStopSound)))
				.then(Commands.literal("stop")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(MusicCommand::executeStopAll)))
				.then(Commands.literal("reload")
						.executes(MusicCommand::executeReload)));
	}

	private static int executePlayMusic(CommandContext<CommandSourceStack> ctx, int volume, float pitch, Vec3 position, float maxDistance) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "name");
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		CommandSourceStack source = ctx.getSource();

		for (ServerPlayer player : targets) {
			MusicRemotes.getRemote().playMusic(player, name, volume, pitch, true, true, position, maxDistance, false);
		}

		String where = position == null
				? "global"
				: "at " + position.x + " " + position.y + " " + position.z + " (range " + maxDistance + ")";
		source.sendSuccess(() -> Component.literal("Playing custom music '" + name + "' (Volume: " + volume + "%, Pitch: " + pitch + ", " + where + ") for " + describeTargets(targets)), true);
		return targets.size();
	}

	private static int executeStopMusic(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		CommandSourceStack source = ctx.getSource();

		for (ServerPlayer player : targets) {
			MusicRemotes.getRemote().stopMusic(player, true);
		}

		source.sendSuccess(() -> Component.literal("Stopped custom music for " + describeTargets(targets)), true);
		return targets.size();
	}

	private static int executePlaySound(CommandContext<CommandSourceStack> ctx, int volume, float pitch, Vec3 position, float maxDistance) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "name");
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		CommandSourceStack source = ctx.getSource();

		for (ServerPlayer player : targets) {
			MusicRemotes.getRemote().playSound(player, name, volume, pitch, position, maxDistance);
		}

		String where = position == null
				? "global"
				: "at " + position.x + " " + position.y + " " + position.z + " (range " + maxDistance + ")";
		source.sendSuccess(() -> Component.literal("Playing custom sound '" + name + "' (Volume: " + volume + "%, Pitch: " + pitch + ", " + where + ") for " + describeTargets(targets)), true);
		return targets.size();
	}

	private static int executeStopSound(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		CommandSourceStack source = ctx.getSource();

		for (ServerPlayer player : targets) {
			MusicRemotes.getRemote().stopSound(player);
		}

		source.sendSuccess(() -> Component.literal("Stopped custom sound effects for " + describeTargets(targets)), true);
		return targets.size();
	}

	private static int executeStopAll(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		CommandSourceStack source = ctx.getSource();

		for (ServerPlayer player : targets) {
			MusicRemotes.getRemote().stopAll(player);
		}

		source.sendSuccess(() -> Component.literal("Stopped all custom audio for " + describeTargets(targets)), true);
		return targets.size();
	}

	private static int executeReload(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		Map<String, Long> manifest = MusicLibrary.scanTrackSizes();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			MusicRemotes.getRemote().syncLibrary(player, manifest);
		}

		source.sendSuccess(() -> Component.literal("Rescanned custom music folder."), true);
		return 1;
	}

	private static String describeTargets(Collection<ServerPlayer> targets) {
		if (targets.size() == 1) {
			return targets.iterator().next().getGameProfile().getName();
		}
		return targets.size() + " players";
	}
}
