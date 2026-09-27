package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import com.putzwirk.mapmakermusic.block.condition.BuiltinConditionKinds;
import com.putzwirk.mapmakermusic.block.condition.CommandConditionKind;
import com.putzwirk.mapmakermusic.block.condition.ConditionKindRegistry;
import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MusicQueueTest {

	@BeforeAll
	public static void setup() {
		BuiltinConditionKinds.registerAll();
	}

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
		MusicCondition condition = ConditionKindRegistry.get(CommandConditionKind.ID).newDefault();
		condition.params().putString(CommandConditionKind.COMMAND_KEY, "mmcheck time range 10 100");
		queue.getRuleRoot().getKids().add(condition);

		MusicQueue loaded = MusicQueue.load(queue.save());
		assertEquals(2, loaded.getTracks().size());
		assertEquals("cave_theme", loaded.getTracks().get(0).getTrack());
		assertEquals(80, loaded.getTracks().get(0).getVolume());
		assertTrue(loaded.getTracks().get(1).isStop());
		assertFalse(loaded.isLoop());
		assertEquals(1, loaded.getRuleRoot().countLeaves());
		assertEquals("Run mmcheck time range 10 10...", ((MusicCondition) loaded.getRuleRoot().getKids().get(0)).describe());
	}

	@Test
	public void chainSelectorMigratesToPlayerRule() {
		MusicQueue queue = new MusicQueue();
		queue.getTracks().add(new MusicQueue.PlaylistItem("theme"));
		MusicBlockEntity box = new MusicBlockEntity(null, BlockPos.ZERO, null);
		box.setTriggerMode(MusicBlockEntity.TriggerMode.CHAIN);
		box.setAreaGate(false);
		box.setListenerSelector("Steve");
		box.getQueues().add(queue);
		MusicBlockEntity loaded = new MusicBlockEntity(null, BlockPos.ZERO, null);
		loaded.load(box.getUpdateTag());
		assertEquals("@a", loaded.getListenerSelector());
		assertEquals(1, loaded.getQueues().get(0).getRuleRoot().countLeaves());
		MusicCondition migrated = (MusicCondition) loaded.getQueues().get(0).getRuleRoot().getKids().get(0);
		assertEquals(CommandConditionKind.ID, migrated.getKindId());
		assertEquals("Run execute if entity @s[nam...", migrated.describe());
		MusicBlockEntity reloud = new MusicBlockEntity(null, BlockPos.ZERO, null);
		reloud.load(loaded.getUpdateTag());
		assertEquals(1, reloud.getQueues().get(0).getRuleRoot().countLeaves());
	}

	@Test
	public void shuffledOrderIsAPermutation() {
		for (int size : new int[] {0, 1, 2, 5}) {
			List<Integer> order = MusicQueue.shuffledOrder(size);
			assertEquals(size, order.size());
			assertEquals(size, new java.util.HashSet<>(order).size());
			for (int i = 0; i < size; i++) {
				assertTrue(order.contains(i));
			}
		}
	}

	@Test
	public void shufflePersistsAndCopies() {
		MusicQueue queue = new MusicQueue();
		assertFalse(queue.isShuffle());
		queue.setShuffle(true);
		assertTrue(MusicQueue.load(queue.save()).isShuffle());
		assertTrue(queue.copy().isShuffle());
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
