package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
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

	@Test
	public void nonPositionalIsAlwaysAudible() {
		assertTrue(MusicBlockTicker.audible(false, new BlockPos(0, 0, 0), 16, 1000, 1000, 1000));
	}

	@Test
	public void positionalAudibleInsideRadius() {
		BlockPos pos = new BlockPos(100, 64, 100);
		assertTrue(MusicBlockTicker.audible(true, pos, 16, 100.5, 64.5, 100.5));
		assertTrue(MusicBlockTicker.audible(true, pos, 16, 108.5, 64.5, 100.5));
	}

	@Test
	public void positionalInaudibleOutsideRadius() {
		BlockPos pos = new BlockPos(100, 64, 100);
		assertFalse(MusicBlockTicker.audible(true, pos, 16, 200.5, 64.5, 100.5));
		assertFalse(MusicBlockTicker.audible(true, pos, 16, 100.5, 200.5, 100.5));
	}

	@Test
	public void audibleBoundaryIsInclusive() {
		BlockPos pos = new BlockPos(0, 0, 0);
		assertTrue(MusicBlockTicker.audible(true, pos, 10, 10.5, 0.5, 0.5));
		assertFalse(MusicBlockTicker.audible(true, pos, 10, 10.6, 0.5, 0.5));
	}

	@Test
	public void impulseRetriggerSkippedForSameTrack() {
		List<MusicQueue.PlaylistItem> tracks = List.of(new MusicQueue.PlaylistItem("song"), new MusicQueue.PlaylistItem("other"));
		MusicBlockTicker.PlaybackState state = new MusicBlockTicker.PlaybackState();
		state.queueIndex = 0;
		state.trackIndex = 0;
		assertTrue(MusicBlockTicker.impulseRestartSkipped(tracks, 0, 0, state));
	}

	@Test
	public void impulseRetriggerRestartsForOtherTrack() {
		List<MusicQueue.PlaylistItem> tracks = List.of(new MusicQueue.PlaylistItem("song"), new MusicQueue.PlaylistItem("other"));
		MusicBlockTicker.PlaybackState state = new MusicBlockTicker.PlaybackState();
		state.queueIndex = 0;
		state.trackIndex = 1;
		assertFalse(MusicBlockTicker.impulseRestartSkipped(tracks, 0, 0, state));
	}

	@Test
	public void impulseRetriggerRestartsWhenStoppedOrMissing() {
		List<MusicQueue.PlaylistItem> tracks = List.of(new MusicQueue.PlaylistItem("song"));
		MusicBlockTicker.PlaybackState stopped = new MusicBlockTicker.PlaybackState();
		stopped.queueIndex = 0;
		stopped.trackIndex = 0;
		stopped.stopped = true;
		assertFalse(MusicBlockTicker.impulseRestartSkipped(tracks, 0, 0, stopped));
		assertFalse(MusicBlockTicker.impulseRestartSkipped(tracks, 0, 0, null));
	}

	@Test
	public void impulseRetriggerRestartsForOtherQueue() {
		List<MusicQueue.PlaylistItem> tracks = List.of(new MusicQueue.PlaylistItem("song"));
		MusicBlockTicker.PlaybackState state = new MusicBlockTicker.PlaybackState();
		state.queueIndex = 1;
		state.trackIndex = 0;
		assertFalse(MusicBlockTicker.impulseRestartSkipped(tracks, 0, 0, state));
	}
}
