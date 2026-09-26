package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class MusicRuleEvaluatorTest {

	private static MusicCondition goneMode() {
		MusicCondition condition = MusicCondition.Type.ENTITY_ALIVE.newDefault();
		condition.getType().cycleMode(condition);
		return condition;
	}

	@Test
	public void aliveConditionMatchesOnlyWhenAlive() {
		MusicCondition condition = MusicCondition.Type.ENTITY_ALIVE.newDefault();
		assertTrue(MusicRuleEvaluator.bossMatches(true, condition));
		assertFalse(MusicRuleEvaluator.bossMatches(false, condition));
	}

	@Test
	public void goneConditionMatchesOnlyWhenDead() {
		MusicCondition condition = goneMode();
		assertFalse(MusicRuleEvaluator.bossMatches(true, condition));
		assertTrue(MusicRuleEvaluator.bossMatches(false, condition));
	}

	@Test
	public void unresolvableIdCountsAsNotAlive() {
		MusicCondition alive = MusicCondition.Type.ENTITY_ALIVE.newDefault();
		alive.setText("not_a_real_entity");
		assertFalse(MusicRuleEvaluator.bossMatches(false, alive));

		MusicCondition gone = goneMode();
		gone.setText("not_a_real_entity");
		assertTrue(MusicRuleEvaluator.bossMatches(false, gone));
	}

	@Test
	public void goneModeRoundTripsThroughSaveLoad() {
		MusicCondition condition = goneMode();
		condition.setText("minecraft:ender_dragon");
		MusicCondition loaded = MusicCondition.load(condition.save());
		assertEquals(MusicCondition.Type.ENTITY_ALIVE, loaded.getType());
		assertEquals("gone", loaded.getType().modeOf(loaded));
		assertEquals("minecraft:ender_dragon", loaded.getText());
		assertTrue(MusicRuleEvaluator.bossMatches(false, loaded));
		assertFalse(MusicRuleEvaluator.bossMatches(true, loaded));
	}
}
