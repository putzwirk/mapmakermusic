package com.putzwirk.mapmakermusic.block;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class EntityAliveCache {
	private Object owner;
	private long tick = -1L;
	private final Map<String, Boolean> alive = new HashMap<>();

	public synchronized boolean get(Object owner, long tick, String key, BooleanSupplier scan) {
		if (owner != this.owner || tick != this.tick) {
			alive.clear();
			this.owner = owner;
			this.tick = tick;
		}
		Boolean hit = alive.get(key);
		if (hit != null) {
			return hit;
		}
		boolean result = scan.getAsBoolean();
		alive.put(key, result);
		return result;
	}

	public synchronized void clear() {
		alive.clear();
		tick = -1L;
		owner = null;
	}
}
