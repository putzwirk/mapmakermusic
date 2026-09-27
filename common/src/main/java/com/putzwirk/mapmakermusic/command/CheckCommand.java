package com.putzwirk.mapmakermusic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;

public final class CheckCommand {

	private CheckCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("mmcheck")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("time")
						.then(Commands.literal("day").executes(ctx -> timeResult(ctx, "day", 0, 0)))
						.then(Commands.literal("night").executes(ctx -> timeResult(ctx, "night", 0, 0)))
						.then(Commands.literal("range")
								.then(Commands.argument("min", IntegerArgumentType.integer(0, 23999))
										.then(Commands.argument("max", IntegerArgumentType.integer(0, 23999))
												.executes(ctx -> timeResult(ctx, "range",
														IntegerArgumentType.getInteger(ctx, "min"),
														IntegerArgumentType.getInteger(ctx, "max")))))))
				.then(Commands.literal("weather")
						.then(Commands.literal("clear").executes(ctx -> weatherResult(ctx, "clear")))
						.then(Commands.literal("rain").executes(ctx -> weatherResult(ctx, "rain")))
						.then(Commands.literal("thunder").executes(ctx -> weatherResult(ctx, "thunder"))))
				.then(Commands.literal("biome")
						.then(Commands.argument("biome", ResourceLocationArgument.id())
								.suggests((ctx, builder) -> suggestBiomes(ctx.getSource(), builder))
								.executes(ctx -> biomeResult(ctx, ResourceLocationArgument.getId(ctx, "biome"))))));
	}

	private static int timeResult(CommandContext<CommandSourceStack> ctx, String mode, int min, int max) throws CommandSyntaxException {
		return timeMatches(mode, min, max, ctx.getSource().getLevel().getDayTime() % 24000L) ? 1 : 0;
	}

	private static int weatherResult(CommandContext<CommandSourceStack> ctx, String mode) {
		return weatherMatches(mode, ctx.getSource().getLevel().isRaining(), ctx.getSource().getLevel().isThundering()) ? 1 : 0;
	}

	private static int biomeResult(CommandContext<CommandSourceStack> ctx, ResourceLocation want) {
		CommandSourceStack source = ctx.getSource();
		Holder<Biome> biome = source.getLevel().getBiome(BlockPos.containing(source.getPosition()));
		String actual = biome.unwrapKey().map(key -> key.location().toString()).orElse("");
		return biomeMatches(want.toString(), actual) ? 1 : 0;
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBiomes(CommandSourceStack source, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		MinecraftServer server = source.getServer();
		if (server != null) {
			String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
			List<String> ids = server.registryAccess().registryOrThrow(Registries.BIOME).keySet().stream().map(Object::toString).sorted().toList();
			for (String id : ids) {
				if (id.toLowerCase(Locale.ROOT).startsWith(remaining)) {
					builder.suggest(id);
				}
			}
		}
		return builder.buildFuture();
	}

	static boolean timeMatches(String mode, int min, int max, long dayTime) {
		return switch (mode) {
			case "day" -> dayTime < 12000L;
			case "night" -> dayTime >= 12000L;
			default -> dayTime >= min && dayTime <= max;
		};
	}

	static boolean weatherMatches(String mode, boolean raining, boolean thundering) {
		return switch (mode) {
			case "rain" -> raining;
			case "thunder" -> thundering;
			default -> !raining;
		};
	}

	static boolean biomeMatches(String want, String actual) {
		return actual.equalsIgnoreCase(want);
	}
}
