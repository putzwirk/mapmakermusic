package com.putzwirk.mapmakermusic.block.condition;

import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public final class TimeConditionKind implements ConditionKind {

	public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "time");
	public static final String MODE_KEY = "mode";
	public static final String MIN_KEY = "min";
	public static final String MAX_KEY = "max";
	private static final List<String> MODES = List.of("day", "night", "range");

	@Override
	public ResourceLocation id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Time";
	}

	@Override
	public boolean evaluate(MusicCondition condition, ConditionContext ctx) {
		long time = ctx.player().level().getDayTime() % 24000L;
		String mode = modeOf(condition);
		if (mode.equals("day")) {
			return time < 12000L;
		}
		if (mode.equals("night")) {
			return time >= 12000L;
		}
		return time >= ConditionParams.dbl(condition.params(), MIN_KEY, 0)
				&& time <= ConditionParams.dbl(condition.params(), MAX_KEY, 12000);
	}

	@Override
	public String describe(MusicCondition condition) {
		return switch (modeOf(condition)) {
			case "day" -> "Daytime";
			case "night" -> "Night";
			default -> "Time " + ConditionParams.format(ConditionParams.dbl(condition.params(), MIN_KEY, 0))
					+ ".." + ConditionParams.format(ConditionParams.dbl(condition.params(), MAX_KEY, 12000));
		};
	}

	@Override
	public MusicCondition newDefault() {
		MusicCondition condition = new MusicCondition(ID);
		condition.params().putString(MODE_KEY, "day");
		condition.params().putDouble(MAX_KEY, 12000);
		return condition;
	}

	@Override
	public List<FieldSpec> editorFields() {
		return List.of(FieldSpec.modeCycle(), FieldSpec.range(MIN_KEY, MAX_KEY, "Min", "Max"));
	}

	@Override
	public List<String> modes() {
		return MODES;
	}

	@Override
	public String modeOf(MusicCondition condition) {
		return ConditionParams.str(condition.params(), MODE_KEY, "day").toLowerCase(Locale.ROOT);
	}

	@Override
	public void cycleMode(MusicCondition condition) {
		String current = modeOf(condition);
		condition.params().putString(MODE_KEY, MODES.get((MODES.indexOf(current) + 1) % MODES.size()));
	}

	@Override
	public boolean fieldVisible(FieldSpec spec, MusicCondition condition) {
		return spec.type() != FieldSpec.FieldType.RANGE || modeOf(condition).equals("range");
	}
}
