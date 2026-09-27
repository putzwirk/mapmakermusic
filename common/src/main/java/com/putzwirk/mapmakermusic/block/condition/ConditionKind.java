package com.putzwirk.mapmakermusic.block.condition;

import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public interface ConditionKind {

	ResourceLocation id();

	String displayName();

	boolean evaluate(MusicCondition condition, ConditionContext ctx);

	String describe(MusicCondition condition);

	MusicCondition newDefault();

	List<FieldSpec> editorFields();

	default List<String> modes() {
		return List.of();
	}

	default String modeOf(MusicCondition condition) {
		return null;
	}

	default void cycleMode(MusicCondition condition) {
	}

	default boolean needsAreaGate() {
		return false;
	}

	default boolean fieldVisible(FieldSpec spec, MusicCondition condition) {
		return true;
	}

	default String runAction(String actionId, MusicCondition condition, Minecraft minecraft) {
		return null;
	}

	default int editorWidth() {
		return 248;
	}

	default int editorHeight() {
		return 150;
	}

	default List<String> editorNotes() {
		return List.of();
	}

	default List<Preset> editorPresets() {
		return List.of();
	}
}
