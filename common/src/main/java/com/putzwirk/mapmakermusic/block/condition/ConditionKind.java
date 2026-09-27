package com.putzwirk.mapmakermusic.block.condition;

import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public interface ConditionKind {

	ResourceLocation id();

	String legacyName();

	String displayName();

	boolean evaluate(MusicCondition condition, ConditionContext ctx);

	String describe(MusicCondition condition);

	MusicCondition newDefault();

	List<FieldSpec> editorFields();

	void saveExtra(MusicCondition condition, CompoundTag tag);

	void loadExtra(MusicCondition condition, CompoundTag tag);

	default List<String> modes() {
		return List.of();
	}

	default String modeOf(MusicCondition condition) {
		return null;
	}

	default void cycleMode(MusicCondition condition) {
	}

	default String textHint() {
		return null;
	}

	default boolean hasRange() {
		return false;
	}

	default String suggestKey() {
		return null;
	}

	default String runAction(String actionId, MusicCondition condition, Minecraft minecraft) {
		return null;
	}
}
