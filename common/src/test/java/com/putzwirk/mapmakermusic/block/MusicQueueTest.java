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
	public void conditionMatchDefaultsToAll() {
		assertEquals(MusicQueue.ConditionMatch.ALL, new MusicQueue().getConditionMatch());
	}

	@Test
	public void conditionMatchPersistsAndCopies() {
		MusicQueue queue = new MusicQueue();
		queue.setConditionMatch(MusicQueue.ConditionMatch.ANY);
		MusicQueue loaded = MusicQueue.load(queue.save());
		assertEquals(MusicQueue.ConditionMatch.ANY, loaded.getConditionMatch());
		assertEquals(MusicQueue.ConditionMatch.ANY, queue.copy().getConditionMatch());
	}

	@Test
	public void conditionMatchNullFallsBackToAll() {
		MusicQueue queue = new MusicQueue();
		queue.setConditionMatch(null);
		assertEquals(MusicQueue.ConditionMatch.ALL, queue.getConditionMatch());
	}

	@Test
	public void saveLoadRoundtripWithStop() {
		MusicQueue queue = new MusicQueue();
		queue.getTracks().add(new MusicQueue.PlaylistItem("cave_theme", 80, 1.0f));
		queue.getTracks().add(new MusicQueue.PlaylistItem("STOP"));
		queue.setLoop(false);
		queue.setConditionMatch(MusicQueue.ConditionMatch.ANY);
		MusicCondition condition = MusicCondition.Type.POS_X.newDefault();
		condition.setMin(10);
		condition.setMax(100);
		queue.getConditions().add(condition);

		MusicQueue loaded = MusicQueue.load(queue.save());
		assertEquals(2, loaded.getTracks().size());
		assertEquals("cave_theme", loaded.getTracks().get(0).getTrack());
		assertEquals(80, loaded.getTracks().get(0).getVolume());
		assertTrue(loaded.getTracks().get(1).isStop());
		assertFalse(loaded.isLoop());
		assertEquals(1, loaded.getConditions().size());
		assertEquals("X 10..100", loaded.getConditions().get(0).describe());
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
