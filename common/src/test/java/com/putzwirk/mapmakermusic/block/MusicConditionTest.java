package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.putzwirk.mapmakermusic.block.condition.BuiltinConditionKinds;
import com.putzwirk.mapmakermusic.block.condition.ConditionKindRegistry;
import com.putzwirk.mapmakermusic.block.condition.CoordinatesConditionKind;
import com.putzwirk.mapmakermusic.block.condition.EntityAliveConditionKind;
import com.putzwirk.mapmakermusic.block.condition.InBiomeConditionKind;
import com.putzwirk.mapmakermusic.block.condition.PlayerConditionKind;
import com.putzwirk.mapmakermusic.block.condition.PlayerHealthConditionKind;
import com.putzwirk.mapmakermusic.block.condition.PlayerHungerConditionKind;
import com.putzwirk.mapmakermusic.block.condition.ScoreboardConditionKind;
import com.putzwirk.mapmakermusic.block.condition.TimeConditionKind;
import com.putzwirk.mapmakermusic.block.condition.WeatherConditionKind;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class MusicConditionTest {

	@BeforeAll
	public static void setup() {
		BuiltinConditionKinds.registerAll();
	}

	private static MusicCondition def(ResourceLocation id) {
		return ConditionKindRegistry.get(id).newDefault();
	}

	@Test
	public void defaults() {
		assertEquals("day", def(TimeConditionKind.ID).params().getString(TimeConditionKind.MODE_KEY));
		assertEquals("clear", def(WeatherConditionKind.ID).params().getString(WeatherConditionKind.MODE_KEY));
		assertEquals(20.0, def(PlayerHealthConditionKind.ID).params().getDouble(PlayerHealthConditionKind.MAX_KEY));
		assertEquals(20.0, def(PlayerHungerConditionKind.ID).params().getDouble(PlayerHungerConditionKind.MAX_KEY));
		assertEquals("minecraft:wither", def(EntityAliveConditionKind.ID).params().getString(EntityAliveConditionKind.ENTITY_KEY));
		assertEquals("minecraft:plains", def(InBiomeConditionKind.ID).params().getString(InBiomeConditionKind.BIOME_KEY));
		assertTrue(Double.isNaN(CoordinatesConditionKind.bound(def(CoordinatesConditionKind.ID).params(), 0)));
	}

	@Test
	public void timeModesCycle() {
		MusicCondition condition = def(TimeConditionKind.ID);
		assertEquals(List.of("day", "night", "range"), condition.kind().modes());
		condition.kind().cycleMode(condition);
		assertEquals("night", condition.params().getString(TimeConditionKind.MODE_KEY));
		condition.kind().cycleMode(condition);
		assertEquals("range", condition.params().getString(TimeConditionKind.MODE_KEY));
		condition.kind().cycleMode(condition);
		assertEquals("day", condition.params().getString(TimeConditionKind.MODE_KEY));
	}

	@Test
	public void entityCountTagDescribe() {
		MusicCondition both = def(EntityAliveConditionKind.ID);
		both.params().putString(EntityAliveConditionKind.ENTITY_KEY, "minecraft:villager");
		both.params().putString(EntityAliveConditionKind.TAG_KEY, "boss");
		both.params().putString(EntityAliveConditionKind.OP_KEY, "exactly");
		both.params().putDouble(EntityAliveConditionKind.THRESHOLD_KEY, 0);
		assertEquals("Villager #boss == 0", both.describe());

		MusicCondition tagOnly = def(EntityAliveConditionKind.ID);
		tagOnly.params().putString(EntityAliveConditionKind.ENTITY_KEY, "");
		tagOnly.params().putString(EntityAliveConditionKind.TAG_KEY, "boss");
		tagOnly.params().putString(EntityAliveConditionKind.OP_KEY, "at least");
		tagOnly.params().putDouble(EntityAliveConditionKind.THRESHOLD_KEY, 1);
		assertEquals("#boss >= 1", tagOnly.describe());
	}

	@Test
	public void entityCountTagRoundtrips() {
		MusicCondition condition = def(EntityAliveConditionKind.ID);
		condition.params().putString(EntityAliveConditionKind.ENTITY_KEY, "minecraft:villager");
		condition.params().putString(EntityAliveConditionKind.TAG_KEY, "boss");
		condition.params().putString(EntityAliveConditionKind.OP_KEY, "at most");
		condition.params().putDouble(EntityAliveConditionKind.THRESHOLD_KEY, 3);
		MusicCondition loaded = MusicCondition.load(condition.save());
		assertEquals(EntityAliveConditionKind.ID, loaded.getKindId());
		assertEquals("boss", loaded.params().getString(EntityAliveConditionKind.TAG_KEY));
		assertEquals("at most", loaded.kind().modeOf(loaded));
		assertEquals("Villager #boss <= 3", loaded.describe());
		assertEquals("boss", condition.copy().params().getString(EntityAliveConditionKind.TAG_KEY));
	}

	@Test
	public void entityCountModeCycles() {
		MusicCondition condition = def(EntityAliveConditionKind.ID);
		assertEquals(List.of("at least", "at most", "exactly"), condition.kind().modes());
		assertEquals("at least", condition.kind().modeOf(condition));
		assertEquals("Wither >= 1", condition.describe());
		condition.kind().cycleMode(condition);
		assertEquals("at most", condition.kind().modeOf(condition));
		assertEquals("Wither <= 1", condition.describe());
		condition.kind().cycleMode(condition);
		assertEquals("exactly", condition.kind().modeOf(condition));
		assertEquals("Wither == 1", condition.describe());
		condition.kind().cycleMode(condition);
		assertEquals("at least", condition.kind().modeOf(condition));
	}

	@Test
	public void modelessKindsHaveNoMode() {
		MusicCondition condition = def(CoordinatesConditionKind.ID);
		assertTrue(condition.kind().modes().isEmpty());
		assertNull(condition.kind().modeOf(condition));
	}

	@ParameterizedTest
	@CsvSource({"mapmakermusic:time, Daytime", "mapmakermusic:weather, Clear"})
	public void describeDefaults(String id, String expected) {
		assertEquals(expected, def(new ResourceLocation(id)).describe());
	}

	@Test
	public void longIdsDescribePretty() {
		MusicCondition biome = def(InBiomeConditionKind.ID);
		biome.params().putString(InBiomeConditionKind.BIOME_KEY, "minecraft:old_growth_birch_forest");
		assertEquals("Biome Old growth birch forest", biome.describe());

		MusicCondition entity = def(EntityAliveConditionKind.ID);
		entity.params().putString(EntityAliveConditionKind.ENTITY_KEY, "minecraft:iron_golem");
		assertEquals("Iron golem >= 1", entity.describe());
	}

	@Test
	public void playerDescribe() {
		MusicCondition condition = def(PlayerConditionKind.ID);
		assertEquals("@a", condition.params().getString(PlayerConditionKind.SELECTOR_KEY));
		assertEquals("Any player", condition.describe());
		condition.params().putString(PlayerConditionKind.SELECTOR_KEY, "Steve");
		assertEquals("Player Steve", condition.describe());
	}

	@Test
	public void scoreDescribe() {
		MusicCondition condition = def(ScoreboardConditionKind.ID);
		condition.params().putString(ScoreboardConditionKind.OBJECTIVE_KEY, "kills");
		condition.params().putDouble(ScoreboardConditionKind.MIN_KEY, 5);
		condition.params().putDouble(ScoreboardConditionKind.MAX_KEY, 10);
		assertEquals("Score kills 5..10", condition.describe());
	}

	@Test
	public void coordinatesDescribe() {
		MusicCondition condition = def(CoordinatesConditionKind.ID);
		assertEquals("Anywhere", condition.describe());
		condition.params().putDouble(CoordinatesConditionKind.BOUND_KEYS[0], 10);
		assertEquals("At X 10", condition.describe());
		condition.params().putDouble(CoordinatesConditionKind.BOUND_KEYS[1], 100);
		condition.params().putDouble(CoordinatesConditionKind.BOUND_KEYS[4], 5);
		condition.params().putDouble(CoordinatesConditionKind.BOUND_KEYS[5], 5);
		assertEquals("At X 10..100, Z 5", condition.describe());
	}

	@Test
	public void coordinatesRoundtrip() {
		MusicCondition condition = def(CoordinatesConditionKind.ID);
		condition.params().putDouble(CoordinatesConditionKind.BOUND_KEYS[0], 10);
		condition.params().putDouble(CoordinatesConditionKind.BOUND_KEYS[3], 64);
		MusicCondition loaded = MusicCondition.load(condition.save());
		assertEquals(CoordinatesConditionKind.ID, loaded.getKindId());
		assertEquals(10.0, CoordinatesConditionKind.bound(loaded.params(), 0));
		assertTrue(Double.isNaN(CoordinatesConditionKind.bound(loaded.params(), 1)));
		assertEquals(64.0, CoordinatesConditionKind.bound(loaded.params(), 3));
		assertEquals("At X 10, Y 64", loaded.describe());
	}

	@Test
	public void saveLoadRoundtrip() {
		MusicCondition condition = def(ScoreboardConditionKind.ID);
		condition.params().putString(ScoreboardConditionKind.OBJECTIVE_KEY, "kills");
		condition.params().putDouble(ScoreboardConditionKind.MIN_KEY, 3);
		condition.params().putDouble(ScoreboardConditionKind.MAX_KEY, 7);
		MusicCondition loaded = MusicCondition.load(condition.save());
		assertEquals(ScoreboardConditionKind.ID, loaded.getKindId());
		assertEquals("kills", loaded.params().getString(ScoreboardConditionKind.OBJECTIVE_KEY));
		assertEquals(3.0, loaded.params().getDouble(ScoreboardConditionKind.MIN_KEY));
		assertEquals(7.0, loaded.params().getDouble(ScoreboardConditionKind.MAX_KEY));
	}

	@Test
	public void unknownKindDescribesAndRoundtrips() {
		MusicCondition condition = new MusicCondition(new ResourceLocation("mapmakermusic", "nope"));
		assertNull(condition.kind());
		assertEquals("Unknown condition", condition.describe());
		MusicCondition loaded = MusicCondition.load(condition.save());
		assertEquals(new ResourceLocation("mapmakermusic", "nope"), loaded.getKindId());
		assertEquals("Unknown condition", loaded.describe());
	}

	@Test
	public void copyIsIndependent() {
		MusicCondition condition = def(CoordinatesConditionKind.ID);
		MusicCondition copy = condition.copy();
		copy.params().putDouble(CoordinatesConditionKind.BOUND_KEYS[2], 50);
		assertTrue(Double.isNaN(CoordinatesConditionKind.bound(condition.params(), 2)));
		assertFalse(copy == condition);
		assertEquals("At Y 50", copy.describe());
	}
}
