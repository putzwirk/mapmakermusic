package com.putzwirk.mapmakermusic.block.condition;

import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class PlayerConditionKind implements ConditionKind {

	public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "player");
	public static final String SELECTOR_KEY = "selector";

	@Override
	public ResourceLocation id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "For player";
	}

	@Override
	public boolean evaluate(MusicCondition condition, ConditionContext ctx) {
		ServerPlayer player = ctx.player();
		String selector = ConditionParams.str(condition.params(), SELECTOR_KEY, "").trim();
		if (selector.isEmpty() || selector.equals("@a")) {
			return true;
		}
		if (selector.equals("@p")) {
			ServerPlayer nearest = null;
			double nearestDist = Double.MAX_VALUE;
			Vec3 boxVec = Vec3.atCenterOf(ctx.boxPos());
			for (ServerPlayer other : player.serverLevel().players().stream().filter(p -> p instanceof ServerPlayer).map(p -> (ServerPlayer) p).toList()) {
				double dist = other.distanceToSqr(boxVec);
				if (dist < nearestDist) {
					nearestDist = dist;
					nearest = other;
				}
			}
			return nearest != null && nearest.getUUID().equals(player.getUUID());
		}
		return player.getScoreboardName().equalsIgnoreCase(selector) || player.getUUID().toString().equalsIgnoreCase(selector);
	}

	@Override
	public String describe(MusicCondition condition) {
		String selector = ConditionParams.str(condition.params(), SELECTOR_KEY, "").trim();
		if (selector.isEmpty() || selector.equals("@a")) {
			return "Any player";
		}
		return "Player " + ConditionParams.str(condition.params(), SELECTOR_KEY, "");
	}

	@Override
	public MusicCondition newDefault() {
		MusicCondition condition = new MusicCondition(ID);
		condition.params().putString(SELECTOR_KEY, "@a");
		return condition;
	}

	@Override
	public List<FieldSpec> editorFields() {
		return List.of(FieldSpec.entityId(SELECTOR_KEY, "@a, @p or name", "player"));
	}
}
