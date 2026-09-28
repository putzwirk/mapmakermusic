package com.putzwirk.mapmakermusic.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestion;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

public class CheckCommandTest {

	@Test
	public void timeDayBoundaries() {
		assertTrue(CheckCommand.timeMatches("day", 0, 0, 0));
		assertTrue(CheckCommand.timeMatches("day", 0, 0, 11999));
		assertFalse(CheckCommand.timeMatches("day", 0, 0, 12000));
		assertFalse(CheckCommand.timeMatches("day", 0, 0, 23999));
	}

	@Test
	public void timeNightBoundaries() {
		assertFalse(CheckCommand.timeMatches("night", 0, 0, 0));
		assertFalse(CheckCommand.timeMatches("night", 0, 0, 11999));
		assertTrue(CheckCommand.timeMatches("night", 0, 0, 12000));
		assertTrue(CheckCommand.timeMatches("night", 0, 0, 23999));
	}

	@Test
	public void timeRangeMatches() {
		assertTrue(CheckCommand.timeMatches("range", 100, 500, 100));
		assertTrue(CheckCommand.timeMatches("range", 100, 500, 500));
		assertTrue(CheckCommand.timeMatches("range", 100, 500, 300));
		assertFalse(CheckCommand.timeMatches("range", 100, 500, 99));
		assertFalse(CheckCommand.timeMatches("range", 100, 500, 501));
	}

	@Test
	public void weatherModes() {
		assertTrue(CheckCommand.weatherMatches("rain", true, false));
		assertFalse(CheckCommand.weatherMatches("rain", false, false));
		assertTrue(CheckCommand.weatherMatches("thunder", true, true));
		assertFalse(CheckCommand.weatherMatches("thunder", true, false));
		assertTrue(CheckCommand.weatherMatches("clear", false, false));
		assertFalse(CheckCommand.weatherMatches("clear", true, false));
		assertFalse(CheckCommand.weatherMatches("clear", true, true));
	}

	@Test
	public void biomeMatchesCaseInsensitive() {
		assertTrue(CheckCommand.biomeMatches("minecraft:plains", "minecraft:plains"));
		assertTrue(CheckCommand.biomeMatches("minecraft:plains", "MINECRAFT:PLAINS"));
		assertFalse(CheckCommand.biomeMatches("minecraft:plains", "minecraft:desert"));
		assertFalse(CheckCommand.biomeMatches("minecraft:plains", ""));
	}

	@Test
	public void rangeCommandBindsMinAndMax() {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		CheckCommand.register(dispatcher);
		CommandSourceStack source = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 2, "", Component.literal("test"), null, null);
		CommandContext<CommandSourceStack> context = dispatcher.parse("mmcheck time range 0 20000", source).getContext().build("mmcheck time range 0 20000");
		assertEquals(0, IntegerArgumentType.getInteger(context, "min"));
		assertEquals(20000, IntegerArgumentType.getInteger(context, "max"));
	}

	@Test
	public void rangeArgsSuggestDayAnchors() {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		CheckCommand.register(dispatcher);
		CommandSourceStack source = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 2, "", Component.literal("test"), null, null);
		List<String> texts = dispatcher.getCompletionSuggestions(dispatcher.parse("mmcheck time range 6", source)).join().getList()
				.stream().map(Suggestion::getText).toList();
		assertTrue(texts.contains("6000"));
		List<String> empty = dispatcher.getCompletionSuggestions(dispatcher.parse("mmcheck time range ", source)).join().getList()
				.stream().map(Suggestion::getText).toList();
		assertTrue(empty.containsAll(List.of("0", "6000", "12000", "18000")));
	}
}
