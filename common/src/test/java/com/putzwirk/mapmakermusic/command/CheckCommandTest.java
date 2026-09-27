package com.putzwirk.mapmakermusic.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
