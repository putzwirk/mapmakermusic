package com.putzwirk.mapmakermusic.block.condition;

import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class CoordinatesConditionKind implements ConditionKind {

	public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "coordinates");
	public static final String[] BOUND_KEYS = {"x1", "x2", "y1", "y2", "z1", "z2"};
	public static final String[] BOUND_HINTS = {"X1", "X2", "Y1", "Y2", "Z1", "Z2"};
	private static final String[] AXIS_NAMES = {"X", "Y", "Z"};

	@Override
	public ResourceLocation id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Player coordinates";
	}

	@Override
	public boolean evaluate(MusicCondition condition, ConditionContext ctx) {
		CompoundTag params = condition.params();
		return axisMatches(ctx.player().getX(), bound(params, 0), bound(params, 1))
				&& axisMatches(ctx.player().getY(), bound(params, 2), bound(params, 3))
				&& axisMatches(ctx.player().getZ(), bound(params, 4), bound(params, 5));
	}

	@Override
	public String describe(MusicCondition condition) {
		CompoundTag params = condition.params();
		List<String> parts = new ArrayList<>();
		for (int axis = 0; axis < 3; axis++) {
			double lo = bound(params, axis * 2);
			double hi = bound(params, axis * 2 + 1);
			if (Double.isNaN(lo) && Double.isNaN(hi)) {
				continue;
			}
			if (Double.isNaN(lo)) {
				lo = hi;
			}
			if (Double.isNaN(hi)) {
				hi = lo;
			}
			parts.add(AXIS_NAMES[axis] + " " + (lo == hi ? ConditionParams.format(lo) : ConditionParams.format(lo) + ".." + ConditionParams.format(hi)));
		}
		if (parts.isEmpty()) {
			return "Anywhere";
		}
		return "At " + String.join(", ", parts);
	}

	@Override
	public MusicCondition newDefault() {
		return new MusicCondition(ID);
	}

	@Override
	public List<FieldSpec> editorFields() {
		return List.of(FieldSpec.bounds());
	}

	public static double bound(CompoundTag params, int index) {
		if (index < 0 || index >= BOUND_KEYS.length || !params.contains(BOUND_KEYS[index])) {
			return Double.NaN;
		}
		return params.getDouble(BOUND_KEYS[index]);
	}

	private static boolean axisMatches(double value, double min, double max) {
		if (Double.isNaN(min) && Double.isNaN(max)) {
			return true;
		}
		if (Double.isNaN(min)) {
			min = max;
		}
		if (Double.isNaN(max)) {
			max = min;
		}
		return value >= min && value <= max;
	}
}
