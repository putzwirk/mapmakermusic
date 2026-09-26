package com.putzwirk.mapmakermusic.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class MusicCondition {

	public enum Type {
		TIME("Time"),
		WEATHER("Weather"),
		SCOREBOARD("Scoreboard"),
		PLAYER("Player"),
		PLAYER_HEALTH("Player health"),
		PLAYER_HUNGER("Player hunger"),
		ENTITY_ALIVE("Entity alive"),
		IN_BIOME("In biome"),
		COORDINATES("Player coordinates");

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
				case PLAYER -> "@a, @p or name";
				case ENTITY_ALIVE -> "entity id";
				case IN_BIOME -> "biome id";
				default -> null;
			};
		}

		public boolean hasRange() {
			return switch (this) {
				case TIME, SCOREBOARD, PLAYER_HEALTH, PLAYER_HUNGER -> true;
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
				case PLAYER -> condition.text = "@a";
				case PLAYER_HEALTH, PLAYER_HUNGER -> condition.max = 20;
				case ENTITY_ALIVE -> {
					condition.text = "minecraft:wither";
					condition.min = 1;
				}
				case IN_BIOME -> condition.text = "minecraft:plains";
				case COORDINATES -> condition.bounds = nanBounds();
			}
			return condition;
		}
	}

	public static double[] nanBounds() {
		double[] bounds = new double[6];
		java.util.Arrays.fill(bounds, Double.NaN);
		return bounds;
	}

	private Type type;
	private String text = "";
	private double min;
	private double max;
	private double[] bounds;

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

	public double getBound(int index) {
		if (bounds != null && index >= 0 && index < bounds.length) {
			return bounds[index];
		}
		return Double.NaN;
	}

	public void setBound(int index, double value) {
		if (index < 0 || index >= 6) {
			return;
		}
		if (bounds == null) {
			bounds = nanBounds();
		}
		bounds[index] = value;
	}

	public MusicCondition copy() {
		MusicCondition copy = new MusicCondition(type);
		copy.text = text;
		copy.min = min;
		copy.max = max;
		copy.bounds = bounds == null ? null : bounds.clone();
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
			case PLAYER -> {
				String selector = text == null ? "" : text.trim();
				if (selector.isEmpty() || selector.equals("@a")) {
					yield "Any player";
				}
				yield "Player " + text;
			}
			case PLAYER_HEALTH -> "Health " + format(min) + ".." + format(max);
			case PLAYER_HUNGER -> "Hunger " + format(min) + ".." + format(max);
			case ENTITY_ALIVE -> cap(text) + (min >= 0.5 ? " alive" : " gone");
			case IN_BIOME -> "Biome " + text;
			case COORDINATES -> describeCoords();
		};
	}

	private String describeCoords() {
		String[] names = {"X", "Y", "Z"};
		List<String> parts = new ArrayList<>();
		for (int axis = 0; axis < 3; axis++) {
			double lo = getBound(axis * 2);
			double hi = getBound(axis * 2 + 1);
			if (Double.isNaN(lo) && Double.isNaN(hi)) {
				continue;
			}
			if (Double.isNaN(lo)) {
				lo = hi;
			}
			if (Double.isNaN(hi)) {
				hi = lo;
			}
			parts.add(names[axis] + " " + (lo == hi ? format(lo) : format(lo) + ".." + format(hi)));
		}
		if (parts.isEmpty()) {
			return "Anywhere";
		}
		return "At " + String.join(", ", parts);
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
		if (type == Type.COORDINATES && bounds != null) {
			ListTag boundList = new ListTag();
			for (double value : bounds) {
				boundList.add(DoubleTag.valueOf(Double.isNaN(value) ? Double.MAX_VALUE : value));
			}
			tag.put("Bounds", boundList);
		}
		return tag;
	}

	public static MusicCondition load(CompoundTag tag) {
		Type type = Type.TIME;
		int legacyAxis = -1;
		if (tag.contains("Type", Tag.TAG_STRING)) {
			String name = tag.getString("Type");
			try {
				type = Type.valueOf(name);
			} catch (IllegalArgumentException ignored) {
				legacyAxis = axisOfName(name);
				if (legacyAxis >= 0) {
					type = Type.COORDINATES;
				}
			}
		} else if (tag.getInt("Type") == 6) {
			type = Type.COORDINATES;
			legacyAxis = 1;
		} else {
			type = legacy(tag.getInt("Type"));
		}
		MusicCondition condition = new MusicCondition(type);
		condition.text = tag.getString("Text");
		condition.min = tag.getDouble("Min");
		condition.max = tag.getDouble("Max");
		if (type == Type.COORDINATES) {
			if (legacyAxis >= 0) {
				double[] migrated = nanBounds();
				migrated[legacyAxis * 2] = condition.min;
				migrated[legacyAxis * 2 + 1] = condition.max;
				condition.bounds = migrated;
			} else if (tag.contains("Bounds")) {
				ListTag boundList = tag.getList("Bounds", Tag.TAG_DOUBLE);
				double[] migrated = nanBounds();
				for (int i = 0; i < Math.min(6, boundList.size()); i++) {
					double value = boundList.getDouble(i);
					migrated[i] = value == Double.MAX_VALUE ? Double.NaN : value;
				}
				condition.bounds = migrated;
			} else {
				condition.bounds = nanBounds();
			}
		}
		return condition;
	}

	private static int axisOfName(String name) {
		return switch (name) {
			case "POS_X" -> 0;
			case "POS_Y" -> 1;
			case "POS_Z" -> 2;
			default -> -1;
		};
	}

	private static Type legacy(int ordinal) {
		return switch (ordinal) {
			case 1 -> Type.WEATHER;
			case 2 -> Type.SCOREBOARD;
			case 3 -> Type.PLAYER_HEALTH;
			case 4 -> Type.ENTITY_ALIVE;
			case 5 -> Type.IN_BIOME;
			default -> Type.TIME;
		};
	}
}
