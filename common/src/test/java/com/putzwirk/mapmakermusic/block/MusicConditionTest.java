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
		assertEquals(-100.0, MusicCondition.Type.POS_X.newDefault().getMin());
		assertEquals(100.0, MusicCondition.Type.POS_X.newDefault().getMax());
		assertEquals(320.0, MusicCondition.Type.POS_Y.newDefault().getMax());
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
	public void entityAliveModeUsesFlag() {
		MusicCondition condition = MusicCondition.Type.ENTITY_ALIVE.newDefault();
		assertEquals("alive", condition.getType().modeOf(condition));
		condition.getType().cycleMode(condition);
		assertEquals("gone", condition.getType().modeOf(condition));
		assertTrue(condition.describe().endsWith("gone"));
	}

	@Test
	public void modelessTypesHaveNoMode() {
		assertTrue(MusicCondition.Type.POS_X.modes().isEmpty());
		assertEquals(null, MusicCondition.Type.POS_X.modeOf(MusicCondition.Type.POS_X.newDefault()));
	}

	@ParameterizedTest
	@CsvSource({"TIME, Daytime", "WEATHER, Clear"})
	public void describeDefaults(String type, String expected) {
		MusicCondition condition = MusicCondition.Type.valueOf(type).newDefault();
		assertEquals(expected, condition.describe());
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
	public void positionDescribe() {
		MusicCondition condition = MusicCondition.Type.POS_X.newDefault();
		condition.setMin(10);
		condition.setMax(100);
		assertEquals("X 10..100", condition.describe());
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
	@CsvSource({"0, TIME", "1, WEATHER", "2, SCOREBOARD", "3, PLAYER_HEALTH", "4, ENTITY_ALIVE", "5, IN_BIOME", "6, POS_Y", "99, TIME"})
	public void legacyOrdinalsMigrate(int ordinal, String expected) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Type", ordinal);
		tag.putString("Text", "x");
		assertEquals(MusicCondition.Type.valueOf(expected), MusicCondition.load(tag).getType());
	}

	@Test
	public void copyIsIndependent() {
		MusicCondition condition = MusicCondition.Type.POS_Y.newDefault();
		MusicCondition copy = condition.copy();
		copy.setMin(50);
		assertEquals(0.0, condition.getMin());
		assertFalse(copy == condition);
		assertTrue(condition.describe().contains("Y"));
	}
}
