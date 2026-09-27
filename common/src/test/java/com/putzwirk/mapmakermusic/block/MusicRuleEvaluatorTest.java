package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.putzwirk.mapmakermusic.block.condition.EntityAliveConditionKind;
import org.junit.jupiter.api.Test;

public class MusicRuleEvaluatorTest {

	@Test
	public void atLeastMatches() {
		assertTrue(EntityAliveConditionKind.matchesCount("at least", 2, 2));
		assertTrue(EntityAliveConditionKind.matchesCount("at least", 2, 5));
		assertFalse(EntityAliveConditionKind.matchesCount("at least", 2, 1));
		assertFalse(EntityAliveConditionKind.matchesCount("at least", 1, 0));
	}

	@Test
	public void atMostMatches() {
		assertTrue(EntityAliveConditionKind.matchesCount("at most", 3, 3));
		assertTrue(EntityAliveConditionKind.matchesCount("at most", 3, 0));
		assertFalse(EntityAliveConditionKind.matchesCount("at most", 3, 4));
	}

	@Test
	public void exactlyMatches() {
		assertTrue(EntityAliveConditionKind.matchesCount("exactly", 0, 0));
		assertFalse(EntityAliveConditionKind.matchesCount("exactly", 0, 1));
	}

	@Test
	public void unknownOpFallsBackToAtLeast() {
		assertTrue(EntityAliveConditionKind.matchesCount("bogus", 1, 3));
		assertFalse(EntityAliveConditionKind.matchesCount("bogus", 1, 0));
	}
}
