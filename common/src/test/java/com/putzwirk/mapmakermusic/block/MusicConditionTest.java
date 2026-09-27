package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.putzwirk.mapmakermusic.block.condition.BuiltinConditionKinds;
import com.putzwirk.mapmakermusic.block.condition.CommandConditionKind;
import com.putzwirk.mapmakermusic.block.condition.ConditionKindRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MusicConditionTest {

	@BeforeAll
	public static void setup() {
		BuiltinConditionKinds.registerAll();
	}

	private static MusicCondition cmd(String text) {
		MusicCondition condition = ConditionKindRegistry.get(CommandConditionKind.ID).newDefault();
		condition.params().putString(CommandConditionKind.COMMAND_KEY, text);
		return condition;
	}

	@Test
	public void defaultIsEmptyCommand() {
		MusicCondition condition = ConditionKindRegistry.get(CommandConditionKind.ID).newDefault();
		assertEquals(CommandConditionKind.ID, condition.getKindId());
		assertEquals("", condition.params().getString(CommandConditionKind.COMMAND_KEY));
		assertEquals("Command", condition.describe());
	}

	@Test
	public void playerNameCommand() {
		assertEquals("execute if entity @s[name=Steve] run xp query @s levels", CommandConditionKind.playerNameCommand("Steve"));
		assertEquals("Run execute if entity @s[nam...", cmd(CommandConditionKind.playerNameCommand("Steve")).describe());
	}

	@Test
	public void saveLoadRoundtrip() {
		MusicCondition loaded = MusicCondition.load(cmd("mmcheck time day").save());
		assertEquals(CommandConditionKind.ID, loaded.getKindId());
		assertEquals("mmcheck time day", loaded.params().getString(CommandConditionKind.COMMAND_KEY));
		assertEquals("Run mmcheck time day", loaded.describe());
	}

	@Test
	public void unknownKindDescribesAndRoundtrips() {
		MusicCondition condition = new MusicCondition(new ResourceLocation("mapmakermusic", "nope"));
		assertNull(condition.kind());
		assertEquals("Unknown condition", condition.describe());
		MusicCondition loaded = MusicCondition.load(condition.save());
		assertEquals(new ResourceLocation("mapmakermusic", "nope"), loaded.getKindId());
		assertEquals("Unknown condition", loaded.describe());
	}

	@Test
	public void copyIsIndependent() {
		MusicCondition condition = cmd("mmcheck time day");
		MusicCondition copy = condition.copy();
		copy.params().putString(CommandConditionKind.COMMAND_KEY, "mmcheck time night");
		assertEquals("mmcheck time day", condition.params().getString(CommandConditionKind.COMMAND_KEY));
		assertFalse(copy == condition);
		assertEquals("Run mmcheck time night", copy.describe());
		assertTrue(condition.describe().equals("Run mmcheck time day"));
	}
}
