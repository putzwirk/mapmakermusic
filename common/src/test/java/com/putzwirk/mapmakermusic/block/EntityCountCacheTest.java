package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class EntityCountCacheTest {

	@Test
	public void sharedAcrossManyEvaluationsInOneTick() {
		EntityCountCache cache = new EntityCountCache();
		Object server = new Object();
		AtomicInteger scans = new AtomicInteger();

		for (int box = 0; box < 4; box++) {
			for (int player = 0; player < 3; player++) {
				int count = cache.get(server, 100L, "minecraft:wither", () -> {
					scans.incrementAndGet();
					return 2;
				});
				assertEquals(2, count);
			}
		}
		assertEquals(1, scans.get());
	}

	@Test
	public void distinctEntityTypesScanOnceEach() {
		EntityCountCache cache = new EntityCountCache();
		Object server = new Object();
		AtomicInteger scans = new AtomicInteger();

		assertEquals(3, cache.get(server, 7L, "minecraft:wither", () -> {
			scans.incrementAndGet();
			return 3;
		}));
		assertEquals(0, cache.get(server, 7L, "minecraft:ender_dragon", () -> {
			scans.incrementAndGet();
			return 0;
		}));
		assertEquals(2, scans.get());
	}

	@Test
	public void nextTickRescans() {
		EntityCountCache cache = new EntityCountCache();
		Object server = new Object();
		AtomicInteger scans = new AtomicInteger();

		assertEquals(1, cache.get(server, 7L, "minecraft:wither", () -> {
			scans.incrementAndGet();
			return 1;
		}));
		assertEquals(0, cache.get(server, 8L, "minecraft:wither", () -> {
			scans.incrementAndGet();
			return 0;
		}));
		assertEquals(2, scans.get());
	}

	@Test
	public void serverChangeInvalidates() {
		EntityCountCache cache = new EntityCountCache();
		AtomicInteger scans = new AtomicInteger();

		assertEquals(1, cache.get(new Object(), 7L, "minecraft:wither", () -> {
			scans.incrementAndGet();
			return 1;
		}));
		assertEquals(1, cache.get(new Object(), 7L, "minecraft:wither", () -> {
			scans.incrementAndGet();
			return 1;
		}));
		assertEquals(2, scans.get());
	}
}
