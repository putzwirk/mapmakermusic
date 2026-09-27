package com.putzwirk.mapmakermusic.block.condition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class ConditionKindRegistry {

	private static final Map<ResourceLocation, ConditionKind> BY_ID = new LinkedHashMap<>();
	private static final Map<String, ConditionKind> BY_LEGACY = new LinkedHashMap<>();

	private ConditionKindRegistry() {
	}

	public static synchronized void register(ConditionKind kind) {
		BY_ID.put(kind.id(), kind);
		BY_LEGACY.put(kind.legacyName(), kind);
	}

	public static synchronized ConditionKind get(ResourceLocation id) {
		return BY_ID.get(id);
	}

	public static synchronized ConditionKind getByLegacyName(String legacyName) {
		return BY_LEGACY.get(legacyName);
	}

	public static synchronized List<ConditionKind> all() {
		return new ArrayList<>(BY_ID.values());
	}
}
