package com.putzwirk.mapmakermusic.block.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

public class CommandConditionKindTest {

	private static final CommandConditionKind KIND = new CommandConditionKind();

	private static CommandDispatcher<CommandSourceStack> dispatcher() {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		dispatcher.register(Commands.literal("test_pass").executes(context -> 1));
		dispatcher.register(Commands.literal("test_fail").executes(context -> 0));
		return dispatcher;
	}

	private static CommandSourceStack source() {
		return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 2, "", Component.literal("test"), null, null);
	}

	@Test
	public void passingCommandMatches() {
		assertTrue(CommandConditionKind.dispatch(dispatcher(), source(), "test_pass"));
	}

	@Test
	public void failingCommandDoesNotMatch() {
		assertFalse(CommandConditionKind.dispatch(dispatcher(), source(), "test_fail"));
	}

	@Test
	public void leadingSlashIsStripped() {
		assertTrue(CommandConditionKind.dispatch(dispatcher(), source(), "/test_pass"));
	}

	@Test
	public void malformedCommandDoesNotThrow() {
		assertFalse(CommandConditionKind.dispatch(dispatcher(), source(), "no_such_command_xyz"));
		assertFalse(CommandConditionKind.dispatch(dispatcher(), source(), "no_such_command_xyz"));
	}

	@Test
	public void cacheDeduplicatesWithinOneTick() {
		ConditionEvalCache cache = new ConditionEvalCache();
		Object server = new Object();
		AtomicInteger runs = new AtomicInteger();
		for (int i = 0; i < 5; i++) {
			assertTrue(cache.get(server, 100L, "uuid\ntest_pass", () -> {
				runs.incrementAndGet();
				return true;
			}));
		}
		assertEquals(1, runs.get());
	}

	@Test
	public void cacheRescansNextTick() {
		ConditionEvalCache cache = new ConditionEvalCache();
		Object server = new Object();
		AtomicInteger runs = new AtomicInteger();
		assertFalse(cache.get(server, 7L, "uuid\ntest_fail", () -> {
			runs.incrementAndGet();
			return false;
		}));
		assertTrue(cache.get(server, 8L, "uuid\ntest_fail", () -> {
			runs.incrementAndGet();
			return true;
		}));
		assertEquals(2, runs.get());
	}

	@Test
	public void describeShowsTruncatedCommand() {
		assertEquals("Command", KIND.describe(KIND.newDefault()));
		MusicCondition shortCommand = KIND.newDefault();
		shortCommand.params().putString(CommandConditionKind.COMMAND_KEY, "test_pass");
		assertEquals("Run test_pass", KIND.describe(shortCommand));
		MusicCondition longCommand = KIND.newDefault();
		longCommand.params().putString(CommandConditionKind.COMMAND_KEY, "execute if score @s objective matches 1.. run test_pass");
		assertEquals("Run execute if score @s obje...", KIND.describe(longCommand));
	}

	@Test
	public void editorHasCommandBoxAndTestButton() {
		assertTrue(KIND.editorFields().stream().anyMatch(spec -> spec.type() == FieldSpec.FieldType.LONG_TEXT));
		assertTrue(KIND.editorFields().stream().anyMatch(spec -> spec.type() == FieldSpec.FieldType.ACTION));
		assertEquals("empty", KIND.runAction(CommandConditionKind.TEST_ACTION, KIND.newDefault(), null));
	}

	@Test
	public void editorIsWideWithCommandAutocompleteAndExamples() {
		assertEquals(420, KIND.editorWidth());
		assertEquals(300, KIND.editorHeight());
		assertTrue(KIND.editorFields().stream().anyMatch(spec -> spec.type() == FieldSpec.FieldType.LONG_TEXT && "command".equals(spec.suggest())));
		assertEquals(1, KIND.editorNotes().size());
		assertEquals("Click a preset, or type your own command", KIND.editorNotes().get(0));
		assertEquals(9, KIND.editorPresets().size());
		assertTrue(KIND.editorPresets().stream().anyMatch(preset -> preset.label().equals("Day") && preset.command().equals("mmcheck time day")));
		assertTrue(KIND.editorPresets().stream().anyMatch(preset -> preset.label().equals("Health") && preset.command().equals("data get entity @s Health")));
		assertTrue(KIND.editorFields().stream().anyMatch(spec -> spec.type() == FieldSpec.FieldType.PRESETS));
		ConditionKind plain = new ConditionKind() {
			@Override
			public net.minecraft.resources.ResourceLocation id() {
				return new net.minecraft.resources.ResourceLocation("mapmakermusic", "plain");
			}

			@Override
			public String displayName() {
				return "plain";
			}

			@Override
			public boolean evaluate(com.putzwirk.mapmakermusic.block.MusicCondition condition, ConditionContext ctx) {
				return false;
			}

			@Override
			public String describe(com.putzwirk.mapmakermusic.block.MusicCondition condition) {
				return "plain";
			}

			@Override
			public com.putzwirk.mapmakermusic.block.MusicCondition newDefault() {
				return new com.putzwirk.mapmakermusic.block.MusicCondition(id());
			}

			@Override
			public java.util.List<FieldSpec> editorFields() {
				return java.util.List.of();
			}
		};
		assertEquals(248, plain.editorWidth());
		assertEquals(150, plain.editorHeight());
		assertTrue(plain.editorNotes().isEmpty());
	}
}
