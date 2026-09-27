package com.putzwirk.mapmakermusic.block.condition;

import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public final class WeatherConditionKind implements ConditionKind {

	public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "weather");
	public static final String MODE_KEY = "mode";
	private static final List<String> MODES = List.of("clear", "rain", "thunder");

	@Override
	public ResourceLocation id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Weather";
	}

	@Override
	public boolean evaluate(MusicCondition condition, ConditionContext ctx) {
		String mode = modeOf(condition);
		if (mode.equals("rain")) {
			return ctx.player().level().isRaining();
		}
		if (mode.equals("thunder")) {
			return ctx.player().level().isThundering();
		}
		return !ctx.player().level().isRaining();
	}

	@Override
	public String describe(MusicCondition condition) {
		return switch (modeOf(condition)) {
			case "rain" -> "Raining";
			case "thunder" -> "Thundering";
			default -> "Clear";
		};
	}

	@Override
	public MusicCondition newDefault() {
		MusicCondition condition = new MusicCondition(ID);
		condition.params().putString(MODE_KEY, "clear");
		return condition;
	}

	@Override
	public List<FieldSpec> editorFields() {
		return List.of(FieldSpec.modeCycle());
	}

	@Override
	public List<String> modes() {
		return MODES;
	}

	@Override
	public String modeOf(MusicCondition condition) {
		return ConditionParams.str(condition.params(), MODE_KEY, "clear").toLowerCase(Locale.ROOT);
	}

	@Override
	public void cycleMode(MusicCondition condition) {
		String current = modeOf(condition);
		condition.params().putString(MODE_KEY, MODES.get((MODES.indexOf(current) + 1) % MODES.size()));
	}
}
