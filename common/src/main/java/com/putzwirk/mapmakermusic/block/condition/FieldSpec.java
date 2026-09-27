package com.putzwirk.mapmakermusic.block.condition;

public record FieldSpec(FieldType type, String key, String secondKey, String label, String secondLabel, String hint, String suggest) {

	public enum FieldType {
		TEXT,
		LONG_TEXT,
		NUMBER,
		RANGE,
		MODE_CYCLE,
		BOUNDS,
		ENTITY_ID,
		ACTION
	}

	public static FieldSpec text(String key, String label, String hint) {
		return new FieldSpec(FieldType.TEXT, key, null, label, null, hint, null);
	}

	public static FieldSpec longText(String key, String label, String hint) {
		return new FieldSpec(FieldType.LONG_TEXT, key, null, label, null, hint, null);
	}

	public static FieldSpec number(String key, String label) {
		return new FieldSpec(FieldType.NUMBER, key, null, label, null, null, null);
	}

	public static FieldSpec range(String minKey, String maxKey, String minLabel, String maxLabel) {
		return new FieldSpec(FieldType.RANGE, minKey, maxKey, minLabel, maxLabel, null, null);
	}

	public static FieldSpec modeCycle() {
		return new FieldSpec(FieldType.MODE_CYCLE, null, null, "", null, null, null);
	}

	public static FieldSpec bounds() {
		return new FieldSpec(FieldType.BOUNDS, null, null, "", null, null, null);
	}

	public static FieldSpec entityId(String key, String hint, String suggest) {
		return new FieldSpec(FieldType.ENTITY_ID, key, null, "", null, hint, suggest);
	}

	public static FieldSpec action(String actionId, String label) {
		return new FieldSpec(FieldType.ACTION, actionId, null, label, null, null, null);
	}
}
