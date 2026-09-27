package com.putzwirk.mapmakermusic.block;

import com.putzwirk.mapmakermusic.block.condition.ConditionContext;
import com.putzwirk.mapmakermusic.block.condition.ConditionKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

public final class MusicRuleEvaluator {

	private MusicRuleEvaluator() {
	}

	public static boolean matches(MusicQueue queue, ServerPlayer player, BlockPos boxPos, AABB area, ServerLevel level) {
		return matchesNode(queue.getRuleRoot(), player, boxPos, area, level);
	}

	public static boolean matchesNode(Object node, ServerPlayer player, BlockPos boxPos, AABB area, ServerLevel level) {
		if (node instanceof ConditionGroup group) {
			if (group.getKids().isEmpty()) {
				return true;
			}
			if (group.getOp() == ConditionGroup.Op.ANY) {
				for (Object kid : group.getKids()) {
					if (matchesNode(kid, player, boxPos, area, level)) {
						return true;
					}
				}
				return false;
			}
			for (Object kid : group.getKids()) {
				if (!matchesNode(kid, player, boxPos, area, level)) {
					return false;
				}
			}
			return true;
		}
		return matches((MusicCondition) node, player, boxPos, area, level);
	}

	public static boolean matches(MusicCondition condition, ServerPlayer player, BlockPos boxPos, AABB area, ServerLevel level) {
		ConditionKind kind = condition.kind();
		return kind != null && kind.evaluate(condition, new ConditionContext(player, boxPos, area, level));
	}
}
