package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import com.putzwirk.mapmakermusic.block.condition.BuiltinConditionKinds;
import com.putzwirk.mapmakermusic.block.condition.CommandConditionKind;
import com.putzwirk.mapmakermusic.block.condition.ConditionKindRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ConditionGroupTest {

	@BeforeAll
	public static void setup() {
		BuiltinConditionKinds.registerAll();
	}

	private static MusicCondition cmd(String text) {
		MusicCondition condition = ConditionKindRegistry.get(CommandConditionKind.ID).newDefault();
		condition.params().putString(CommandConditionKind.COMMAND_KEY, text);
		return condition;
	}

	@Test
	public void nestedDescribe() {
		ConditionGroup root = new ConditionGroup();
		root.setOp(ConditionGroup.Op.ANY);
		MusicCondition clear = cmd("mmcheck weather clear");
		root.getKids().add(clear);

		ConditionGroup rain = new ConditionGroup();
		rain.setOp(ConditionGroup.Op.ALL);
		MusicCondition raining = cmd("mmcheck weather rain");
		rain.getKids().add(raining);

		ConditionGroup time = new ConditionGroup();
		time.setOp(ConditionGroup.Op.ALL);
		MusicCondition after = cmd("mmcheck time range 100 500");
		time.getKids().add(after);
		rain.getKids().add(time);
		root.getKids().add(rain);

		assertEquals("When Run mmcheck weather clear or (Run mmcheck weather rain and Run mmcheck time range 100 500)", root.describeRules());
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
		root.getKids().add(cmd("mmcheck time day"));
		assertEquals("When Run mmcheck time day", root.describeRules());
	}

	@Test
	public void childrenAndCounts() {
		ConditionGroup root = new ConditionGroup();
		root.getKids().add(cmd("mmcheck time day"));
		ConditionGroup sub = new ConditionGroup();
		sub.getKids().add(cmd("mmcheck weather clear"));
		root.getKids().add(sub);
		assertEquals("Run mmcheck time day and Run mmcheck weather clear", root.describeChildren());
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
		root.getKids().add(cmd("mmcheck time day"));
		ConditionGroup sub = new ConditionGroup();
		MusicCondition score = cmd("execute if score @s kills matches 0..100");
		sub.getKids().add(score);
		root.getKids().add(sub);

		ConditionGroup loaded = ConditionGroup.load(root.save());
		assertEquals(ConditionGroup.Op.ANY, loaded.getOp());
		assertEquals(2, loaded.countLeaves());
		assertEquals("When Run mmcheck time day or Run execute if score @s kills matches 0..100", loaded.describeRules());
	}

	@Test
	public void legacyQueueMigrates() {
		MusicQueue queue = new MusicQueue();
		queue.getRuleRoot().getKids().add(cmd("mmcheck weather clear"));
		queue.getRuleRoot().setOp(ConditionGroup.Op.ANY);
		MusicQueue loaded = MusicQueue.load(queue.save());
		assertEquals(ConditionGroup.Op.ANY, loaded.getRuleRoot().getOp());
		assertEquals(1, loaded.getRuleRoot().countLeaves());
	}

	@Test
	public void replaceByIdentity() {
		ConditionGroup root = new ConditionGroup();
		MusicCondition original = cmd("mmcheck time day");
		root.getKids().add(original);
		MusicCondition edited = cmd("mmcheck time night");
		assertTrue(root.replaceByIdentity(original, edited));
		assertEquals("Run mmcheck time night", ((MusicCondition) root.getKids().get(0)).describe());
	}

	@Test
	public void collectRowsDepths() {
		ConditionGroup root = new ConditionGroup();
		root.getKids().add(cmd("mmcheck time day"));
		ConditionGroup sub = new ConditionGroup();
		sub.getKids().add(cmd("mmcheck weather clear"));
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
		leaf.put("Leaf", cmd("mmcheck time day").save());
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
		leafKid.put("Leaf", cmd("mmcheck weather clear").save());
		kids.add(leafKid);
		root.put("Kids", kids);

		ConditionGroup loaded = ConditionGroup.load(root);
		assertEquals(1, loaded.countLeaves());
		assertEquals("When Run mmcheck weather clear", loaded.describeRules());
	}

	@Test
	public void resolvePathFindsNestedNodes() {
		ConditionGroup root = new ConditionGroup();
		MusicCondition first = cmd("mmcheck time day");
		ConditionGroup sub = new ConditionGroup();
		MusicCondition nested = cmd("mmcheck weather clear");
		root.getKids().add(first);
		root.getKids().add(sub);
		sub.getKids().add(nested);
		assertSame(root, ConditionGroup.resolvePath(root, List.of()));
		assertSame(first, ConditionGroup.resolvePath(root, List.of(0)));
		assertSame(sub, ConditionGroup.resolvePath(root, List.of(1)));
		assertSame(nested, ConditionGroup.resolvePath(root, List.of(1, 0)));
		assertNull(ConditionGroup.resolvePath(root, List.of(2)));
		assertNull(ConditionGroup.resolvePath(root, List.of(1, 1)));
		assertNull(ConditionGroup.resolvePath(root, List.of(0, 0)));
		assertNull(ConditionGroup.resolvePath(root, List.of(-1)));
	}
}
