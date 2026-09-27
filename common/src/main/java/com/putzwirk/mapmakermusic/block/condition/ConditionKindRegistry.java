package com.putzwirk.mapmakermusic.block.condition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class ConditionKindRegistry {

	private static final Map<ResourceLocation, ConditionKind> BY_ID = new LinkedHashMap<>();

	private ConditionKindRegistry() {
	}

	public static synchronized void register(ConditionKind kind) {
		BY_ID.put(kind.id(), kind);
	}

	public static synchronized ConditionKind get(ResourceLocation id) {
		return BY_ID.get(id);
	}

	public static synchronized List<ConditionKind> all() {
		return new ArrayList<>(BY_ID.values());
	}
}
