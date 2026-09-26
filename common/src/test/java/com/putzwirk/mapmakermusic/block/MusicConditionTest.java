package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class MusicConditionTest {

	@Test
	public void defaults() {
		assertEquals("day", MusicCondition.Type.TIME.newDefault().getText());
		assertEquals("clear", MusicCondition.Type.WEATHER.newDefault().getText());
		assertEquals(20.0, MusicCondition.Type.PLAYER_HEALTH.newDefault().getMax());
		assertEquals(20.0, MusicCondition.Type.PLAYER_HUNGER.newDefault().getMax());
		assertEquals("minecraft:wither", MusicCondition.Type.ENTITY_ALIVE.newDefault().getText());
		assertEquals("minecraft:plains", MusicCondition.Type.IN_BIOME.newDefault().getText());
		assertTrue(Double.isNaN(MusicCondition.Type.COORDINATES.newDefault().getBound(0)));
	}

	@Test
	public void timeModesCycle() {
		MusicCondition condition = MusicCondition.Type.TIME.newDefault();
		assertEquals(List.of("day", "night", "range"), MusicCondition.Type.TIME.modes());
		condition.getType().cycleMode(condition);
		assertEquals("night", condition.getText());
		condition.getType().cycleMode(condition);
		assertEquals("range", condition.getText());
		condition.getType().cycleMode(condition);
		assertEquals("day", condition.getText());
	}

	@Test
	public void entityCountTagDescribe() {
		MusicCondition both = MusicCondition.Type.ENTITY_ALIVE.newDefault();
		both.setText("minecraft:villager");
		both.setTag("boss");
		both.setCountOp("exactly");
		both.setMin(0);
		assertEquals("Villager #boss == 0", both.describe());

		MusicCondition tagOnly = MusicCondition.Type.ENTITY_ALIVE.newDefault();
		tagOnly.setText("");
		tagOnly.setTag("boss");
		tagOnly.setCountOp("at least");
		tagOnly.setMin(1);
		assertEquals("#boss >= 1", tagOnly.describe());
	}

	@Test
	public void entityCountTagRoundtrips() {
		MusicCondition condition = MusicCondition.Type.ENTITY_ALIVE.newDefault();
		condition.setText("minecraft:villager");
		condition.setTag("boss");
		condition.setCountOp("at most");
		condition.setMin(3);
		MusicCondition loaded = MusicCondition.load(condition.save());
		assertEquals("boss", loaded.getTag());
		assertEquals("at most", loaded.getType().modeOf(loaded));
		assertEquals("Villager #boss <= 3", loaded.describe());
		assertEquals("boss", condition.copy().getTag());
	}

	@Test
	public void legacyLoadHasEmptyTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Type", "ENTITY_ALIVE");
		tag.putString("Text", "minecraft:wither");
		tag.putDouble("Min", 1);
		tag.putDouble("Max", 100);
		MusicCondition loaded = MusicCondition.load(tag);
		assertEquals("", loaded.getTag());
		assertEquals("Wither >= 1", loaded.describe());
	}

	@Test
	public void entityCountModeCycles() {
		MusicCondition condition = MusicCondition.Type.ENTITY_ALIVE.newDefault();
		assertEquals(List.of("at least", "at most", "exactly"), MusicCondition.Type.ENTITY_ALIVE.modes());
		assertEquals("at least", condition.getType().modeOf(condition));
		assertEquals("Wither >= 1", condition.describe());
		condition.getType().cycleMode(condition);
		assertEquals("at most", condition.getType().modeOf(condition));
		assertEquals("Wither <= 1", condition.describe());
		condition.getType().cycleMode(condition);
		assertEquals("exactly", condition.getType().modeOf(condition));
		assertEquals("Wither == 1", condition.describe());
		condition.getType().cycleMode(condition);
		assertEquals("at least", condition.getType().modeOf(condition));
	}

	@Test
	public void modelessTypesHaveNoMode() {
		assertTrue(MusicCondition.Type.COORDINATES.modes().isEmpty());
		assertEquals(null, MusicCondition.Type.COORDINATES.modeOf(MusicCondition.Type.COORDINATES.newDefault()));
	}

	@ParameterizedTest
	@CsvSource({"TIME, Daytime", "WEATHER, Clear"})
	public void describeDefaults(String type, String expected) {
		MusicCondition condition = MusicCondition.Type.valueOf(type).newDefault();
		assertEquals(expected, condition.describe());
	}

	@Test
	public void playerDescribe() {
		MusicCondition condition = MusicCondition.Type.PLAYER.newDefault();
		assertEquals("@a", condition.getText());
		assertEquals("Any player", condition.describe());
		condition.setText("Steve");
		assertEquals("Player Steve", condition.describe());
	}

	@Test
	public void scoreDescribe() {
		MusicCondition condition = MusicCondition.Type.SCOREBOARD.newDefault();
		condition.setText("kills");
		condition.setMin(5);
		condition.setMax(10);
		assertEquals("Score kills 5..10", condition.describe());
	}

	@Test
	public void coordinatesDescribe() {
		MusicCondition condition = MusicCondition.Type.COORDINATES.newDefault();
		assertEquals("Anywhere", condition.describe());
		condition.setBound(0, 10);
		assertEquals("At X 10", condition.describe());
		condition.setBound(1, 100);
		condition.setBound(4, 5);
		condition.setBound(5, 5);
		assertEquals("At X 10..100, Z 5", condition.describe());
	}

	@Test
	public void coordinatesRoundtrip() {
		MusicCondition condition = MusicCondition.Type.COORDINATES.newDefault();
		condition.setBound(0, 10);
		condition.setBound(3, 64);
		MusicCondition loaded = MusicCondition.load(condition.save());
		assertEquals(MusicCondition.Type.COORDINATES, loaded.getType());
		assertEquals(10.0, loaded.getBound(0));
		assertTrue(Double.isNaN(loaded.getBound(1)));
		assertEquals(64.0, loaded.getBound(3));
		assertEquals("At X 10, Y 64", loaded.describe());
	}

	@Test
	public void legacyAxisMigrates() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Type", "POS_X");
		tag.putDouble("Min", 10);
		tag.putDouble("Max", 100);
		MusicCondition loaded = MusicCondition.load(tag);
		assertEquals(MusicCondition.Type.COORDINATES, loaded.getType());
		assertEquals("At X 10..100", loaded.describe());
	}

	@Test
	public void saveLoadRoundtrip() {
		MusicCondition condition = MusicCondition.Type.SCOREBOARD.newDefault();
		condition.setText("kills");
		condition.setMin(3);
		condition.setMax(7);
		MusicCondition loaded = MusicCondition.load(condition.save());
		assertEquals(MusicCondition.Type.SCOREBOARD, loaded.getType());
		assertEquals("kills", loaded.getText());
		assertEquals(3.0, loaded.getMin());
		assertEquals(7.0, loaded.getMax());
	}

	@Test
	public void loadUnknownTypeFallsBackToTime() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Type", "NOPE");
		assertEquals(MusicCondition.Type.TIME, MusicCondition.load(tag).getType());
	}

	@ParameterizedTest
	@CsvSource({"0, TIME", "1, WEATHER", "2, SCOREBOARD", "3, PLAYER_HEALTH", "4, ENTITY_ALIVE", "5, IN_BIOME", "6, COORDINATES", "99, TIME"})
	public void legacyOrdinalsMigrate(int ordinal, String expected) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Type", ordinal);
		tag.putString("Text", "x");
		assertEquals(MusicCondition.Type.valueOf(expected), MusicCondition.load(tag).getType());
	}

	@Test
	public void copyIsIndependent() {
		MusicCondition condition = MusicCondition.Type.COORDINATES.newDefault();
		MusicCondition copy = condition.copy();
		copy.setBound(2, 50);
		assertTrue(Double.isNaN(condition.getBound(2)));
		assertFalse(copy == condition);
		assertEquals("At Y 50", copy.describe());
	}
}
