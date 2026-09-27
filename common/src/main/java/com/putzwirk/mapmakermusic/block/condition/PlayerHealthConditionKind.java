package com.putzwirk.mapmakermusic.block.condition;

import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class PlayerHealthConditionKind implements ConditionKind {

	public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "player_health");
	public static final String MIN_KEY = "min";
	public static final String MAX_KEY = "max";

	@Override
	public ResourceLocation id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Player health";
	}

	@Override
	public boolean evaluate(MusicCondition condition, ConditionContext ctx) {
		float health = ctx.player().getHealth();
		return health >= ConditionParams.dbl(condition.params(), MIN_KEY, 0)
				&& health <= ConditionParams.dbl(condition.params(), MAX_KEY, 20);
	}

	@Override
	public String describe(MusicCondition condition) {
		return "Health " + ConditionParams.format(ConditionParams.dbl(condition.params(), MIN_KEY, 0))
				+ ".." + ConditionParams.format(ConditionParams.dbl(condition.params(), MAX_KEY, 20));
	}

	@Override
	public MusicCondition newDefault() {
		MusicCondition condition = new MusicCondition(ID);
		condition.params().putDouble(MAX_KEY, 20);
		return condition;
	}

	@Override
	public List<FieldSpec> editorFields() {
		return List.of(FieldSpec.range(MIN_KEY, MAX_KEY, "Min", "Max"));
	}
}
