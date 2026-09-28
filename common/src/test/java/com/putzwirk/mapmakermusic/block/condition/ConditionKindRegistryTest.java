package com.putzwirk.mapmakermusic.block.condition;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

public class ConditionKindRegistryTest {

	private static class StubKind implements ConditionKind {
		private final String path;

		StubKind(String path) {
			this.path = path;
		}

		@Override
		public ResourceLocation id() {
			return new ResourceLocation("mapmakermusic", path);
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
			return new MusicCondition(id());
		}

		@Override
		public List<FieldSpec> editorFields() {
			return List.of();
		}
	}

	@Test
	public void registersAndResolvesById() {
		StubKind kind = new StubKind("stub_alpha");
		ConditionKindRegistry.register(kind);
		assertSame(kind, ConditionKindRegistry.get(new ResourceLocation("mapmakermusic", "stub_alpha")));
	}

	@Test
	public void unknownIdsResolveToNull() {
		assertNull(ConditionKindRegistry.get(new ResourceLocation("mapmakermusic", "stub_missing")));
	}

	@Test
	public void iterationOrderFollowsRegistrationOrder() {
		StubKind first = new StubKind("stub_order_first");
		StubKind second = new StubKind("stub_order_second");
		ConditionKindRegistry.register(first);
		ConditionKindRegistry.register(second);
		List<ConditionKind> all = ConditionKindRegistry.all();
		assertTrue(all.indexOf(first) < all.indexOf(second));
	}

	@Test
	public void reregisteringSameIdOverwrites() {
		StubKind original = new StubKind("stub_dup");
		StubKind replacement = new StubKind("stub_dup") {
		};
		ConditionKindRegistry.register(original);
		ConditionKindRegistry.register(replacement);
		assertSame(replacement, ConditionKindRegistry.get(new ResourceLocation("mapmakermusic", "stub_dup")));
	}
}
