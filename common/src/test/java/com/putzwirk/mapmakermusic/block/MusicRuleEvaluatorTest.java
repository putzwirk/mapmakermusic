package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

public class MusicRuleEvaluatorTest {

	private static MusicCondition counted(String op, String id, double threshold) {
		MusicCondition condition = MusicCondition.Type.ENTITY_ALIVE.newDefault();
		condition.setCountOp(op);
		condition.setText(id);
		condition.setMin(threshold);
		return condition;
	}

	@Test
	public void atLeastMatches() {
		MusicCondition condition = counted("at least", "minecraft:wither", 2);
		assertTrue(MusicRuleEvaluator.countMatches(2, condition));
		assertTrue(MusicRuleEvaluator.countMatches(5, condition));
		assertFalse(MusicRuleEvaluator.countMatches(1, condition));
		assertFalse(MusicRuleEvaluator.countMatches(0, condition));
	}

	@Test
	public void atMostMatches() {
		MusicCondition condition = counted("at most", "minecraft:villager", 3);
		assertTrue(MusicRuleEvaluator.countMatches(3, condition));
		assertTrue(MusicRuleEvaluator.countMatches(0, condition));
		assertFalse(MusicRuleEvaluator.countMatches(4, condition));
	}

	@Test
	public void exactlyMatches() {
		MusicCondition condition = counted("exactly", "minecraft:villager", 0);
		assertTrue(MusicRuleEvaluator.countMatches(0, condition));
		assertFalse(MusicRuleEvaluator.countMatches(1, condition));
	}

	@Test
	public void unresolvableIdCountsAsZero() {
		MusicCondition condition = counted("exactly", "not_a_real_entity", 0);
		assertTrue(MusicRuleEvaluator.countMatches(0, condition));
		assertFalse(MusicRuleEvaluator.countMatches(0, counted("at least", "not_a_real_entity", 1)));
	}

	@Test
	public void legacyAliveMigratesToAtLeastOne() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Type", "ENTITY_ALIVE");
		tag.putString("Text", "minecraft:wither");
		tag.putDouble("Min", 1);
		tag.putDouble("Max", 100);
		MusicCondition loaded = MusicCondition.load(tag);
		assertEquals("at least", loaded.getType().modeOf(loaded));
		assertEquals(1.0, loaded.getMin());
		assertTrue(MusicRuleEvaluator.countMatches(1, loaded));
		assertFalse(MusicRuleEvaluator.countMatches(0, loaded));
	}

	@Test
	public void legacyGoneMigratesToExactlyZero() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Type", "ENTITY_ALIVE");
		tag.putString("Text", "minecraft:wither");
		tag.putDouble("Min", 0);
		tag.putDouble("Max", 100);
		MusicCondition loaded = MusicCondition.load(tag);
		assertEquals("exactly", loaded.getType().modeOf(loaded));
		assertTrue(MusicRuleEvaluator.countMatches(0, loaded));
		assertFalse(MusicRuleEvaluator.countMatches(1, loaded));
	}

	@Test
	public void countOpRoundTripsThroughSaveLoad() {
		MusicCondition condition = counted("at most", "minecraft:villager", 5);
		MusicCondition loaded = MusicCondition.load(condition.save());
		assertEquals(MusicCondition.Type.ENTITY_ALIVE, loaded.getType());
		assertEquals("at most", loaded.getType().modeOf(loaded));
		assertEquals("minecraft:villager", loaded.getText());
		assertEquals(5.0, loaded.getMin());
		assertTrue(MusicRuleEvaluator.countMatches(5, loaded));
		assertFalse(MusicRuleEvaluator.countMatches(6, loaded));
	}
}
