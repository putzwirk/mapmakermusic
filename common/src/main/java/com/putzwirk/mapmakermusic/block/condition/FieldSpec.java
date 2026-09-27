package com.putzwirk.mapmakermusic.block.condition;

import java.util.List;

public record FieldSpec(FieldType type, String label, String hint, List<String> options, String suggest) {

	public enum FieldType {
		TEXT,
		LONG_TEXT,
		RANGE,
		MODE_CYCLE,
		BOUNDS,
		ENTITY_ID,
		ACTION
	}

	public static FieldSpec text(String label, String hint) {
		return new FieldSpec(FieldType.TEXT, label, hint, List.of(), null);
	}

	public static FieldSpec longText(String label, String hint) {
		return new FieldSpec(FieldType.LONG_TEXT, label, hint, List.of(), null);
	}

	public static FieldSpec range(String minLabel, String maxLabel) {
		return new FieldSpec(FieldType.RANGE, minLabel, maxLabel, List.of(), null);
	}

	public static FieldSpec modeCycle() {
		return new FieldSpec(FieldType.MODE_CYCLE, "", null, List.of(), null);
	}

	public static FieldSpec bounds() {
		return new FieldSpec(FieldType.BOUNDS, "", null, List.of(), null);
	}

	public static FieldSpec entityId(String hint, String suggest) {
		return new FieldSpec(FieldType.ENTITY_ID, "", hint, List.of(), suggest);
	}

	public static FieldSpec action(String actionId, String label) {
		return new FieldSpec(FieldType.ACTION, label, null, List.of(actionId), null);
	}

	public String actionId() {
		return type == FieldType.ACTION && !options.isEmpty() ? options.get(0) : null;
	}
}
