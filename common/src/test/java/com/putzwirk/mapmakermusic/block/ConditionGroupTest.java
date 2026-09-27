package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import com.putzwirk.mapmakermusic.block.condition.BuiltinConditionKinds;
import com.putzwirk.mapmakermusic.block.condition.ConditionKindRegistry;
import com.putzwirk.mapmakermusic.block.condition.ScoreboardConditionKind;
import com.putzwirk.mapmakermusic.block.condition.TimeConditionKind;
import com.putzwirk.mapmakermusic.block.condition.WeatherConditionKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ConditionGroupTest {

	@BeforeAll
	public static void setup() {
		BuiltinConditionKinds.registerAll();
	}

	private static MusicCondition def(ResourceLocation id) {
		return ConditionKindRegistry.get(id).newDefault();
	}

	@Test
	public void nestedDescribe() {
		ConditionGroup root = new ConditionGroup();
		root.setOp(ConditionGroup.Op.ANY);
		MusicCondition clear = def(WeatherConditionKind.ID);
		root.getKids().add(clear);

		ConditionGroup rain = new ConditionGroup();
		rain.setOp(ConditionGroup.Op.ALL);
		MusicCondition raining = def(WeatherConditionKind.ID);
		raining.params().putString(WeatherConditionKind.MODE_KEY, "rain");
		rain.getKids().add(raining);

		ConditionGroup time = new ConditionGroup();
		time.setOp(ConditionGroup.Op.ALL);
		MusicCondition after = def(TimeConditionKind.ID);
		after.params().putString(TimeConditionKind.MODE_KEY, "range");
		after.params().putDouble(TimeConditionKind.MIN_KEY, 100);
		after.params().putDouble(TimeConditionKind.MAX_KEY, 500);
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
	public void emptyGroupsAreSkipped() {
		ConditionGroup root = new ConditionGroup();
		root.setOp(ConditionGroup.Op.ANY);
		root.getKids().add(new ConditionGroup());
		root.getKids().add(def(TimeConditionKind.ID));
		assertEquals("When Daytime", root.describeRules());
	}

	@Test
	public void childrenAndCounts() {
		ConditionGroup root = new ConditionGroup();
		root.getKids().add(def(TimeConditionKind.ID));
		ConditionGroup sub = new ConditionGroup();
		sub.getKids().add(def(WeatherConditionKind.ID));
		root.getKids().add(sub);
		assertEquals("Daytime and Clear", root.describeChildren());
		assertEquals("2 rules", root.ruleCount());
		assertEquals("1 rule", sub.ruleCount());
		assertEquals("", new ConditionGroup().describeChildren());
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
		root.getKids().add(def(TimeConditionKind.ID));
		ConditionGroup sub = new ConditionGroup();
		MusicCondition score = def(ScoreboardConditionKind.ID);
		score.params().putString(ScoreboardConditionKind.OBJECTIVE_KEY, "kills");
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
		queue.getRuleRoot().getKids().add(def(WeatherConditionKind.ID));
		queue.getRuleRoot().setOp(ConditionGroup.Op.ANY);
		MusicQueue loaded = MusicQueue.load(queue.save());
		assertEquals(ConditionGroup.Op.ANY, loaded.getRuleRoot().getOp());
		assertEquals(1, loaded.getRuleRoot().countLeaves());
	}

	@Test
	public void replaceByIdentity() {
		ConditionGroup root = new ConditionGroup();
		MusicCondition original = def(TimeConditionKind.ID);
		root.getKids().add(original);
		MusicCondition edited = def(TimeConditionKind.ID);
		edited.params().putString(TimeConditionKind.MODE_KEY, "night");
		assertTrue(root.replaceByIdentity(original, edited));
		assertEquals("Night", ((MusicCondition) root.getKids().get(0)).describe());
	}

	@Test
	public void collectRowsDepths() {
		ConditionGroup root = new ConditionGroup();
		root.getKids().add(def(TimeConditionKind.ID));
		ConditionGroup sub = new ConditionGroup();
		sub.getKids().add(def(WeatherConditionKind.ID));
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

	@Test
	public void deepNestingTruncatesOnLoad() {
		CompoundTag root = new CompoundTag();
		root.putString("Op", "ALL");
		CompoundTag current = root;
		for (int i = 0; i < 40; i++) {
			CompoundTag group = new CompoundTag();
			group.putString("Op", "ALL");
			CompoundTag kid = new CompoundTag();
			kid.put("Group", group);
			ListTag kids = new ListTag();
			kids.add(kid);
			current.put("Kids", kids);
			current = group;
		}
		CompoundTag leaf = new CompoundTag();
		leaf.put("Leaf", def(TimeConditionKind.ID).save());
		ListTag bottom = new ListTag();
		bottom.add(leaf);
		current.put("Kids", bottom);

		ConditionGroup loaded = ConditionGroup.load(root);
		int depth = 0;
		Object node = loaded;
		while (node instanceof ConditionGroup group && !group.getKids().isEmpty()
				&& group.getKids().get(0) instanceof ConditionGroup next) {
			node = next;
			depth++;
		}
		assertTrue(depth < 40);
		assertTrue(depth <= ConditionGroup.MAX_DEPTH);
		assertEquals(0, loaded.countLeaves());
	}

	@Test
	public void truncationKeepsShallowSiblings() {
		CompoundTag deep = new CompoundTag();
		deep.putString("Op", "ALL");
		CompoundTag current = deep;
		for (int i = 0; i < 40; i++) {
			CompoundTag group = new CompoundTag();
			group.putString("Op", "ALL");
			CompoundTag kid = new CompoundTag();
			kid.put("Group", group);
			ListTag kids = new ListTag();
			kids.add(kid);
			current.put("Kids", kids);
			current = group;
		}
		CompoundTag root = new CompoundTag();
		root.putString("Op", "ALL");
		ListTag kids = new ListTag();
		CompoundTag deepKid = new CompoundTag();
		deepKid.put("Group", deep);
		kids.add(deepKid);
		CompoundTag leafKid = new CompoundTag();
		leafKid.put("Leaf", def(WeatherConditionKind.ID).save());
		kids.add(leafKid);
		root.put("Kids", kids);

		ConditionGroup loaded = ConditionGroup.load(root);
		assertEquals(1, loaded.countLeaves());
		assertEquals("When Clear", loaded.describeRules());
	}
}
