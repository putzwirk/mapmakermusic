package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ConditionGroupTest {

	@Test
	public void nestedDescribe() {
		ConditionGroup root = new ConditionGroup();
		root.setOp(ConditionGroup.Op.ANY);
		MusicCondition clear = MusicCondition.Type.WEATHER.newDefault();
		root.getKids().add(clear);

		ConditionGroup rain = new ConditionGroup();
		rain.setOp(ConditionGroup.Op.ALL);
		MusicCondition raining = MusicCondition.Type.WEATHER.newDefault();
		raining.setText("rain");
		rain.getKids().add(raining);

		ConditionGroup time = new ConditionGroup();
		time.setOp(ConditionGroup.Op.ALL);
		MusicCondition after = MusicCondition.Type.TIME.newDefault();
		after.setText("range");
		after.setMin(100);
		after.setMax(500);
		time.getKids().add(after);
		rain.getKids().add(time);
		root.getKids().add(rain);

		assertEquals("When Clear or (Raining and Time 100..500)", root.describeRules());
		assertEquals(3, root.countLeaves());
	}

	@Test
	public void emptyDescribesAsAlways() {
		assertEquals("Always plays", new ConditionGroup().describeRules());
	}

	@Test
	public void toggleOp() {
		ConditionGroup group = new ConditionGroup();
		assertEquals(ConditionGroup.Op.ALL, group.getOp());
		group.toggleOp();
		assertEquals(ConditionGroup.Op.ANY, group.getOp());
	}

	@Test
	public void saveLoadRoundtrip() {
		ConditionGroup root = new ConditionGroup();
		root.setOp(ConditionGroup.Op.ANY);
		root.getKids().add(MusicCondition.Type.TIME.newDefault());
		ConditionGroup sub = new ConditionGroup();
		MusicCondition score = MusicCondition.Type.SCOREBOARD.newDefault();
		score.setText("kills");
		sub.getKids().add(score);
		root.getKids().add(sub);

		ConditionGroup loaded = ConditionGroup.load(root.save());
		assertEquals(ConditionGroup.Op.ANY, loaded.getOp());
		assertEquals(2, loaded.countLeaves());
		assertEquals("When Daytime or Score kills 0..100", loaded.describeRules());
	}

	@Test
	public void legacyQueueMigrates() {
		MusicQueue queue = new MusicQueue();
		queue.getRuleRoot().getKids().add(MusicCondition.Type.WEATHER.newDefault());
		queue.getRuleRoot().setOp(ConditionGroup.Op.ANY);
		MusicQueue loaded = MusicQueue.load(queue.save());
		assertEquals(ConditionGroup.Op.ANY, loaded.getRuleRoot().getOp());
		assertEquals(1, loaded.getRuleRoot().countLeaves());
	}

	@Test
	public void replaceByIdentity() {
		ConditionGroup root = new ConditionGroup();
		MusicCondition original = MusicCondition.Type.TIME.newDefault();
		root.getKids().add(original);
		MusicCondition edited = MusicCondition.Type.TIME.newDefault();
		edited.setText("night");
		assertTrue(root.replaceByIdentity(original, edited));
		assertEquals("Night", ((MusicCondition) root.getKids().get(0)).describe());
	}

	@Test
	public void collectRowsDepths() {
		ConditionGroup root = new ConditionGroup();
		root.getKids().add(MusicCondition.Type.TIME.newDefault());
		ConditionGroup sub = new ConditionGroup();
		sub.getKids().add(MusicCondition.Type.WEATHER.newDefault());
		root.getKids().add(sub);

		List<ConditionGroup.TreeRow> rows = new ArrayList<>();
		root.collectRows(rows);
		assertEquals(4, rows.size());
		assertEquals(0, rows.get(0).depth());
		assertEquals(1, rows.get(1).depth());
		assertEquals(1, rows.get(2).depth());
		assertEquals(2, rows.get(3).depth());
		assertEquals(sub, rows.get(2).node());
	}
}
