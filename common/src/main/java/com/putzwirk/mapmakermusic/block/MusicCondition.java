package com.putzwirk.mapmakermusic.block;

import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class MusicCondition {

	public enum Type {
		TIME("Time"),
		WEATHER("Weather"),
		SCOREBOARD("Scoreboard"),
		PLAYER_HEALTH("Player health"),
		PLAYER_HUNGER("Player hunger"),
		ENTITY_ALIVE("Entity alive"),
		IN_BIOME("In biome"),
		POS_X("X position"),
		POS_Y("Y position"),
		POS_Z("Z position");

		private final String display;

		Type(String display) {
			this.display = display;
		}

		public String displayName() {
			return display;
		}

		public List<String> modes() {
			return switch (this) {
				case TIME -> List.of("day", "night", "range");
				case WEATHER -> List.of("clear", "rain", "thunder");
				case ENTITY_ALIVE -> List.of("alive", "gone");
				default -> List.of();
			};
		}

		public String modeOf(MusicCondition condition) {
			return switch (this) {
				case TIME, WEATHER -> condition.text.toLowerCase(Locale.ROOT);
				case ENTITY_ALIVE -> condition.min >= 0.5 ? "alive" : "gone";
				default -> null;
			};
		}

		public void cycleMode(MusicCondition condition) {
			switch (this) {
				case TIME, WEATHER -> {
					List<String> options = modes();
					String current = condition.text.toLowerCase(Locale.ROOT);
					condition.text = options.get((options.indexOf(current) + 1) % options.size());
				}
				case ENTITY_ALIVE -> condition.min = condition.min >= 0.5 ? 0 : 1;
				default -> {
				}
			}
		}

		public String textHint() {
			return switch (this) {
				case SCOREBOARD -> "objective";
				case ENTITY_ALIVE -> "entity id";
				case IN_BIOME -> "biome id";
				default -> null;
			};
		}

		public boolean hasRange() {
			return switch (this) {
				case TIME, SCOREBOARD, PLAYER_HEALTH, PLAYER_HUNGER, POS_X, POS_Y, POS_Z -> true;
				default -> false;
			};
		}

		public MusicCondition newDefault() {
			MusicCondition condition = new MusicCondition(this);
			switch (this) {
				case TIME -> {
					condition.text = "day";
					condition.max = 12000;
				}
				case WEATHER -> condition.text = "clear";
				case SCOREBOARD -> {
					condition.text = "objective";
					condition.max = 100;
				}
				case PLAYER_HEALTH, PLAYER_HUNGER -> condition.max = 20;
				case ENTITY_ALIVE -> {
					condition.text = "minecraft:wither";
					condition.min = 1;
				}
				case IN_BIOME -> condition.text = "minecraft:plains";
				case POS_X, POS_Z -> {
					condition.min = -100;
					condition.max = 100;
				}
				case POS_Y -> condition.max = 320;
			}
			return condition;
		}
	}

	private Type type;
	private String text = "";
	private double min;
	private double max;

	public MusicCondition(Type type) {
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text == null ? "" : text;
	}

	public double getMin() {
		return min;
	}

	public void setMin(double min) {
		this.min = min;
	}

	public double getMax() {
		return max;
	}

	public void setMax(double max) {
		this.max = max;
	}

	public MusicCondition copy() {
		MusicCondition copy = new MusicCondition(type);
		copy.text = text;
		copy.min = min;
		copy.max = max;
		return copy;
	}

	public String describe() {
		return switch (type) {
			case TIME -> switch (text.toLowerCase(Locale.ROOT)) {
				case "day" -> "Daytime";
				case "night" -> "Night";
				default -> "Time " + format(min) + ".." + format(max);
			};
			case WEATHER -> switch (text.toLowerCase(Locale.ROOT)) {
				case "rain" -> "Raining";
				case "thunder" -> "Thundering";
				default -> "Clear";
			};
			case SCOREBOARD -> "Score " + text + " " + format(min) + ".." + format(max);
			case PLAYER_HEALTH -> "Health " + format(min) + ".." + format(max);
			case PLAYER_HUNGER -> "Hunger " + format(min) + ".." + format(max);
			case ENTITY_ALIVE -> cap(text) + (min >= 0.5 ? " alive" : " gone");
			case IN_BIOME -> "Biome " + text;
			case POS_X -> "X " + format(min) + ".." + format(max);
			case POS_Y -> "Y " + format(min) + ".." + format(max);
			case POS_Z -> "Z " + format(min) + ".." + format(max);
		};
	}

	private static String format(double value) {
		if (value == Math.rint(value)) {
			return String.valueOf((long) value);
		}
		return String.valueOf(value);
	}

	private static String cap(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Type", type.name());
		tag.putString("Text", text);
		tag.putDouble("Min", min);
		tag.putDouble("Max", max);
		return tag;
	}

	public static MusicCondition load(CompoundTag tag) {
		Type type = Type.TIME;
		if (tag.contains("Type", Tag.TAG_STRING)) {
			try {
				type = Type.valueOf(tag.getString("Type"));
			} catch (IllegalArgumentException ignored) {
			}
		} else {
			type = legacy(tag.getInt("Type"));
		}
		MusicCondition condition = new MusicCondition(type);
		condition.text = tag.getString("Text");
		condition.min = tag.getDouble("Min");
		condition.max = tag.getDouble("Max");
		return condition;
	}

	private static Type legacy(int ordinal) {
		return switch (ordinal) {
			case 1 -> Type.WEATHER;
			case 2 -> Type.SCOREBOARD;
			case 3 -> Type.PLAYER_HEALTH;
			case 4 -> Type.ENTITY_ALIVE;
			case 5 -> Type.IN_BIOME;
			case 6 -> Type.POS_Y;
			default -> Type.TIME;
		};
	}
}
