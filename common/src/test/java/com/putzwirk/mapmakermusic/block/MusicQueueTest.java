package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class MusicQueueTest {

	@Test
	public void stopItemHelpers() {
		assertTrue(MusicQueue.PlaylistItem.isStop("STOP"));
		assertTrue(MusicQueue.PlaylistItem.isStop("stop"));
		assertFalse(MusicQueue.PlaylistItem.isStop("stopper"));
		assertFalse(MusicQueue.PlaylistItem.isStop(null));
		assertTrue(new MusicQueue.PlaylistItem("STOP").isStop());
	}

	@Test
	public void ruleRootDefaultsToAll() {
		assertEquals(ConditionGroup.Op.ALL, new MusicQueue().getRuleRoot().getOp());
	}

	@Test
	public void ruleRootPersistsAndCopies() {
		MusicQueue queue = new MusicQueue();
		queue.getRuleRoot().setOp(ConditionGroup.Op.ANY);
		MusicQueue loaded = MusicQueue.load(queue.save());
		assertEquals(ConditionGroup.Op.ANY, loaded.getRuleRoot().getOp());
		assertEquals(ConditionGroup.Op.ANY, queue.copy().getRuleRoot().getOp());
	}

	@Test
	public void ruleRootOpNullFallsBackToAll() {
		MusicQueue queue = new MusicQueue();
		queue.getRuleRoot().setOp(null);
		assertEquals(ConditionGroup.Op.ALL, queue.getRuleRoot().getOp());
	}

	@Test
	public void saveLoadRoundtripWithStop() {
		MusicQueue queue = new MusicQueue();
		queue.getTracks().add(new MusicQueue.PlaylistItem("cave_theme", 80, 1.0f));
		queue.getTracks().add(new MusicQueue.PlaylistItem("STOP"));
		queue.setLoop(false);
		queue.getRuleRoot().setOp(ConditionGroup.Op.ANY);
		MusicCondition condition = MusicCondition.Type.POS_X.newDefault();
		condition.setMin(10);
		condition.setMax(100);
		queue.getRuleRoot().getKids().add(condition);

		MusicQueue loaded = MusicQueue.load(queue.save());
		assertEquals(2, loaded.getTracks().size());
		assertEquals("cave_theme", loaded.getTracks().get(0).getTrack());
		assertEquals(80, loaded.getTracks().get(0).getVolume());
		assertTrue(loaded.getTracks().get(1).isStop());
		assertFalse(loaded.isLoop());
		assertEquals(1, loaded.getRuleRoot().countLeaves());
		assertEquals("X 10..100", ((MusicCondition) loaded.getRuleRoot().getKids().get(0)).describe());
	}

	@Test
	public void unsetVolumeStaysNull() {
		MusicQueue queue = new MusicQueue();
		queue.getTracks().add(new MusicQueue.PlaylistItem("plain"));
		MusicQueue loaded = MusicQueue.load(queue.save());
		assertNull(loaded.getTracks().get(0).getVolume());
		assertNull(loaded.getTracks().get(0).getPitch());
	}
}
