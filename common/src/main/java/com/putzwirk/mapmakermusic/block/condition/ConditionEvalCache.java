package com.putzwirk.mapmakermusic.block.condition;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class ConditionEvalCache {

	private Object owner;
	private long tick = -1L;
	private final Map<String, Boolean> results = new HashMap<>();

	public synchronized boolean get(Object owner, long tick, String key, BooleanSupplier run) {
		if (owner != this.owner || tick != this.tick) {
			results.clear();
			this.owner = owner;
			this.tick = tick;
		}
		Boolean hit = results.get(key);
		if (hit != null) {
			return hit;
		}
		boolean result = run.getAsBoolean();
		results.put(key, result);
		return result;
	}

	public synchronized void clear() {
		results.clear();
		tick = -1L;
		owner = null;
	}
}
