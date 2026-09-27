package com.putzwirk.mapmakermusic.block.condition;

import net.minecraft.nbt.CompoundTag;

final class ConditionParams {

	private ConditionParams() {
	}

	static String str(CompoundTag params, String key, String fallback) {
		return params.contains(key) ? params.getString(key) : fallback;
	}

	static double dbl(CompoundTag params, String key, double fallback) {
		return params.contains(key) ? params.getDouble(key) : fallback;
	}

	static String format(double value) {
		if (value == Math.rint(value)) {
			return String.valueOf((long) value);
		}
		return String.valueOf(value);
	}

	static String pretty(String value) {
		if (value == null) {
			return "";
		}
		int separator = value.indexOf(':');
		String path = separator >= 0 ? value.substring(separator + 1) : value;
		String spaced = path.replace('_', ' ');
		if (spaced.isEmpty()) {
			return "";
		}
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}
}
