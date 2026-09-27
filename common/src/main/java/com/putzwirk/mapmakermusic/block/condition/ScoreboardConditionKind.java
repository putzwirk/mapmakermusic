package com.putzwirk.mapmakermusic.block.condition;

import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

public final class ScoreboardConditionKind implements ConditionKind {

	public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "scoreboard");
	public static final String OBJECTIVE_KEY = "objective";
	public static final String MIN_KEY = "min";
	public static final String MAX_KEY = "max";

	@Override
	public ResourceLocation id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Scoreboard";
	}

	@Override
	public boolean evaluate(MusicCondition condition, ConditionContext ctx) {
		Scoreboard scoreboard = ctx.player().level().getScoreboard();
		Objective objective = scoreboard.getObjective(ConditionParams.str(condition.params(), OBJECTIVE_KEY, ""));
		if (objective == null) {
			return false;
		}
		int score = scoreboard.getOrCreatePlayerScore(ctx.player().getScoreboardName(), objective).getScore();
		return score >= ConditionParams.dbl(condition.params(), MIN_KEY, 0)
				&& score <= ConditionParams.dbl(condition.params(), MAX_KEY, 100);
	}

	@Override
	public String describe(MusicCondition condition) {
		return "Score " + ConditionParams.str(condition.params(), OBJECTIVE_KEY, "")
				+ " " + ConditionParams.format(ConditionParams.dbl(condition.params(), MIN_KEY, 0))
				+ ".." + ConditionParams.format(ConditionParams.dbl(condition.params(), MAX_KEY, 100));
	}

	@Override
	public MusicCondition newDefault() {
		MusicCondition condition = new MusicCondition(ID);
		condition.params().putString(OBJECTIVE_KEY, "objective");
		condition.params().putDouble(MAX_KEY, 100);
		return condition;
	}

	@Override
	public List<FieldSpec> editorFields() {
		return List.of(
				FieldSpec.text(OBJECTIVE_KEY, "Objective", "objective"),
				FieldSpec.range(MIN_KEY, MAX_KEY, "Min", "Max"));
	}
}
