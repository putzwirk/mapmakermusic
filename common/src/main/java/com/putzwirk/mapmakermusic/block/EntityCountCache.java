package com.putzwirk.mapmakermusic.block;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;

public final class EntityCountCache {
	private Object owner;
	private long tick = -1L;
	private final Map<String, Integer> counts = new HashMap<>();

	public synchronized int get(Object owner, long tick, String key, IntSupplier scan) {
		if (owner != this.owner || tick != this.tick) {
			counts.clear();
			this.owner = owner;
			this.tick = tick;
		}
		Integer hit = counts.get(key);
		if (hit != null) {
			return hit;
		}
		int result = scan.getAsInt();
		counts.put(key, result);
		return result;
	}

	public synchronized void clear() {
		counts.clear();
		tick = -1L;
		owner = null;
	}
}
