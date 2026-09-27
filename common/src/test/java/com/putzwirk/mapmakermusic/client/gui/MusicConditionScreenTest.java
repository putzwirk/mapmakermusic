package com.putzwirk.mapmakermusic.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class MusicConditionScreenTest {

	@Test
	public void completionAtEndAppends() {
		assertEquals("mmcheck biome minecraft:plains",
				MusicConditionScreen.applyCompletion("mmcheck biome ", 14, 14, "minecraft:plains"));
	}

	@Test
	public void completionMidWordKeepsTail() {
		assertEquals("mmcheck biome minecraft:plains extra words",
				MusicConditionScreen.applyCompletion("mmcheck biome minecraf extra words", 14, 22, "minecraft:plains"));
	}

	@Test
	public void completionMidWordConsumesWordFragment() {
		assertEquals("mmcheck biome minecraft:plains",
				MusicConditionScreen.applyCompletion("mmcheck biome minecrafT", 14, 22, "minecraft:plains"));
	}

	@Test
	public void completionBeforeSpaceKeepsSpace() {
		assertEquals("execute if  score",
				MusicConditionScreen.applyCompletion("execute  score", 8, 8, "if "));
	}
}
