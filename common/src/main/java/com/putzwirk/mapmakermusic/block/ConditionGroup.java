package com.putzwirk.mapmakermusic.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class ConditionGroup {

	public static final int MAX_DEPTH = 16;

	public enum Op {
		ALL,
		ANY
	}

	public record TreeRow(Object node, ConditionGroup parent, int index, int depth) {
	}

	private Op op = Op.ALL;
	private final List<Object> kids = new ArrayList<>();

	public Op getOp() {
		return op;
	}

	public void setOp(Op op) {
		this.op = op == null ? Op.ALL : op;
	}

	public void toggleOp() {
		this.op = this.op == Op.ALL ? Op.ANY : Op.ALL;
	}

	public List<Object> getKids() {
		return kids;
	}

	public ConditionGroup copy() {
		ConditionGroup copy = new ConditionGroup();
		copy.op = op;
		for (Object kid : kids) {
			if (kid instanceof ConditionGroup group) {
				copy.kids.add(group.copy());
			} else {
				copy.kids.add(((MusicCondition) kid).copy());
			}
		}
		return copy;
	}

	public String describe() {
		int leaves = countLeaves();
		String unit = leaves == 1 ? "rule" : "rules";
		if (kids.isEmpty()) {
			return "Empty group";
		}
		return (op == Op.ALL ? "All of " : "Any of ") + leaves + " " + unit;
	}

	public String describeRules() {
		if (countLeaves() == 0) {
			return "Always plays";
		}
		return "When " + describeChildren();
	}

	public String describeChildren() {
		List<String> parts = new ArrayList<>();
		for (Object kid : kids) {
			if (kid instanceof ConditionGroup sub && sub.countLeaves() == 0) {
				continue;
			}
			parts.add(describeNode(kid));
		}
		return String.join(op == Op.ALL ? " and " : " or ", parts);
	}

	public String ruleCount() {
		int leaves = countLeaves();
		return leaves + (leaves == 1 ? " rule" : " rules");
	}

	private static String describeNode(Object node) {
		if (node instanceof ConditionGroup group) {
			List<String> parts = new ArrayList<>();
			for (Object kid : group.kids) {
				if (kid instanceof ConditionGroup sub && sub.countLeaves() == 0) {
					continue;
				}
				parts.add(describeNode(kid));
			}
			if (parts.isEmpty()) {
				return "always";
			}
			String joined = String.join(group.op == Op.ALL ? " and " : " or ", parts);
			return parts.size() > 1 ? "(" + joined + ")" : parts.get(0);
		}
		return ((MusicCondition) node).describe();
	}

	public int countLeaves() {
		int count = 0;
		for (Object kid : kids) {
			if (kid instanceof ConditionGroup group) {
				count += group.countLeaves();
			} else {
				count++;
			}
		}
		return count;
	}

	public void collectRows(List<TreeRow> out) {
		collectRows(out, null, -1, 0);
	}

	private void collectRows(List<TreeRow> out, ConditionGroup parent, int index, int depth) {
		out.add(new TreeRow(this, parent, index, depth));
		for (int i = 0; i < kids.size(); i++) {
			Object kid = kids.get(i);
			if (kid instanceof ConditionGroup group) {
				group.collectRows(out, this, i, depth + 1);
			} else {
				out.add(new TreeRow(kid, this, i, depth + 1));
			}
		}
	}

	public boolean replaceByIdentity(Object original, Object replacement) {
		for (int i = 0; i < kids.size(); i++) {
			Object kid = kids.get(i);
			if (kid == original) {
				kids.set(i, replacement);
				return true;
			}
			if (kid instanceof ConditionGroup group && group.replaceByIdentity(original, replacement)) {
				return true;
			}
		}
		return false;
	}

	public static Object resolvePath(ConditionGroup root, List<Integer> path) {
		Object node = root;
		for (int index : path) {
			if (!(node instanceof ConditionGroup group)) {
				return null;
			}
			if (index < 0 || index >= group.getKids().size()) {
				return null;
			}
			node = group.getKids().get(index);
		}
		return node;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Op", op.name());
		ListTag kidList = new ListTag();
		for (Object kid : kids) {
			CompoundTag kidTag = new CompoundTag();
			if (kid instanceof ConditionGroup group) {
				kidTag.put("Group", group.save());
			} else {
				kidTag.put("Leaf", ((MusicCondition) kid).save());
			}
			kidList.add(kidTag);
		}
		tag.put("Kids", kidList);
		return tag;
	}

	public static ConditionGroup load(CompoundTag tag) {
		return load(tag, 0);
	}

	private static ConditionGroup load(CompoundTag tag, int depth) {
		ConditionGroup group = new ConditionGroup();
		try {
			group.op = Op.valueOf(tag.getString("Op"));
		} catch (IllegalArgumentException ignored) {
		}
		ListTag kidList = tag.getList("Kids", Tag.TAG_COMPOUND);
		for (int i = 0; i < kidList.size(); i++) {
			CompoundTag kidTag = kidList.getCompound(i);
			if (kidTag.contains("Group")) {
				if (depth + 1 >= MAX_DEPTH) {
					continue;
				}
				group.kids.add(load(kidTag.getCompound("Group"), depth + 1));
			} else if (kidTag.contains("Leaf")) {
				group.kids.add(MusicCondition.load(kidTag.getCompound("Leaf")));
			}
		}
		return group;
	}
}
