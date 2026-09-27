package com.putzwirk.mapmakermusic.block.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

public class ConditionKindRegistryTest {

	private static final class StubKind implements ConditionKind {
		private final String path;
		private final String legacy;

		StubKind(String path, String legacy) {
			this.path = path;
			this.legacy = legacy;
		}

		@Override
		public ResourceLocation id() {
			return new ResourceLocation("mapmakermusic", path);
		}

		@Override
		public String legacyName() {
			return legacy;
		}

		@Override
		public String displayName() {
			return path;
		}

		@Override
		public boolean evaluate(MusicCondition condition, ConditionContext ctx) {
			return false;
		}

		@Override
		public String describe(MusicCondition condition) {
			return path;
		}

		@Override
		public MusicCondition newDefault() {
			return new MusicCondition(MusicCondition.Type.TIME);
		}

		@Override
		public List<FieldSpec> editorFields() {
			return List.of();
		}

		@Override
		public void saveExtra(MusicCondition condition, CompoundTag tag) {
		}

		@Override
		public void loadExtra(MusicCondition condition, CompoundTag tag) {
		}
	}

	@Test
	public void registersAndResolvesByIdAndLegacyName() {
		StubKind kind = new StubKind("stub_alpha", "STUB_ALPHA");
		ConditionKindRegistry.register(kind);
		assertSame(kind, ConditionKindRegistry.get(new ResourceLocation("mapmakermusic", "stub_alpha")));
		assertSame(kind, ConditionKindRegistry.getByLegacyName("STUB_ALPHA"));
	}

	@Test
	public void unknownIdsResolveToNull() {
		assertNull(ConditionKindRegistry.get(new ResourceLocation("mapmakermusic", "stub_missing")));
		assertNull(ConditionKindRegistry.getByLegacyName("STUB_MISSING"));
	}

	@Test
	public void iterationOrderFollowsRegistrationOrder() {
		StubKind first = new StubKind("stub_order_first", "STUB_ORDER_FIRST");
		StubKind second = new StubKind("stub_order_second", "STUB_ORDER_SECOND");
		ConditionKindRegistry.register(first);
		ConditionKindRegistry.register(second);
		List<ConditionKind> all = ConditionKindRegistry.all();
		assertTrue(all.indexOf(first) < all.indexOf(second));
	}

	@Test
	public void reregisteringSameIdOverwrites() {
		StubKind original = new StubKind("stub_dup", "STUB_DUP_ONE");
		StubKind replacement = new StubKind("stub_dup", "STUB_DUP_TWO");
		ConditionKindRegistry.register(original);
		ConditionKindRegistry.register(replacement);
		assertSame(replacement, ConditionKindRegistry.get(new ResourceLocation("mapmakermusic", "stub_dup")));
		assertSame(replacement, ConditionKindRegistry.getByLegacyName("STUB_DUP_TWO"));
	}
}
