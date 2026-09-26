package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class EntityAliveCacheTest {

	@Test
	public void sharedAcrossManyEvaluationsInOneTick() {
		EntityAliveCache cache = new EntityAliveCache();
		Object server = new Object();
		AtomicInteger scans = new AtomicInteger();

		for (int box = 0; box < 4; box++) {
			for (int player = 0; player < 3; player++) {
				boolean alive = cache.get(server, 100L, "minecraft:wither", () -> {
					scans.incrementAndGet();
					return true;
				});
				assertTrue(alive);
			}
		}
		assertEquals(1, scans.get());
	}

	@Test
	public void distinctEntityTypesScanOnceEach() {
		EntityAliveCache cache = new EntityAliveCache();
		Object server = new Object();
		AtomicInteger scans = new AtomicInteger();

		assertTrue(cache.get(server, 7L, "minecraft:wither", () -> {
			scans.incrementAndGet();
			return true;
		}));
		assertFalse(cache.get(server, 7L, "minecraft:ender_dragon", () -> {
			scans.incrementAndGet();
			return false;
		}));
		assertEquals(2, scans.get());
	}

	@Test
	public void nextTickRescans() {
		EntityAliveCache cache = new EntityAliveCache();
		Object server = new Object();
		AtomicInteger scans = new AtomicInteger();

		assertTrue(cache.get(server, 7L, "minecraft:wither", () -> {
			scans.incrementAndGet();
			return true;
		}));
		assertFalse(cache.get(server, 8L, "minecraft:wither", () -> {
			scans.incrementAndGet();
			return false;
		}));
		assertEquals(2, scans.get());
	}

	@Test
	public void serverChangeInvalidates() {
		EntityAliveCache cache = new EntityAliveCache();
		AtomicInteger scans = new AtomicInteger();

		assertTrue(cache.get(new Object(), 7L, "minecraft:wither", () -> {
			scans.incrementAndGet();
			return true;
		}));
		assertTrue(cache.get(new Object(), 7L, "minecraft:wither", () -> {
			scans.incrementAndGet();
			return true;
		}));
		assertEquals(2, scans.get());
	}
}
