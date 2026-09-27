package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class MusicBlockTickerTest {

	@Test
	public void lowerPrioritySmallerBoxNeverBlocks() {
		assertFalse(MusicBlockTicker.isBlockedBy(10, 1000L, 5L, 3, 8L, 2L));
	}

	@Test
	public void equalPrioritySmallerBoxBlocks() {
		assertTrue(MusicBlockTicker.isBlockedBy(5, 1000L, 5L, 5, 8L, 2L));
	}

	@Test
	public void equalPriorityBiggerBoxDoesNotBlock() {
		assertFalse(MusicBlockTicker.isBlockedBy(5, 8L, 5L, 5, 1000L, 2L));
	}

	@Test
	public void equalVolumeLowerPosBlocks() {
		assertTrue(MusicBlockTicker.isBlockedBy(5, 8L, 9L, 5, 8L, 2L));
		assertFalse(MusicBlockTicker.isBlockedBy(5, 8L, 2L, 5, 8L, 9L));
	}

	@Test
	public void higherPrioritySmallerBoxStillBlocks() {
		assertTrue(MusicBlockTicker.isBlockedBy(3, 1000L, 5L, 10, 8L, 2L));
	}
}
