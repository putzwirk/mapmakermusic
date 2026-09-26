package com.putzwirk.mapmakermusic.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TrackRequestThrottle {
	public static final long COOLDOWN_MILLIS = 1000L;

	private final Map<UUID, Long> lastAllowed = new HashMap<>();

	public synchronized boolean tryAcquire(UUID player, long nowMillis) {
		Long last = lastAllowed.get(player);
		if (last != null && nowMillis - last < COOLDOWN_MILLIS) {
			return false;
		}
		lastAllowed.put(player, nowMillis);
		return true;
	}
}
