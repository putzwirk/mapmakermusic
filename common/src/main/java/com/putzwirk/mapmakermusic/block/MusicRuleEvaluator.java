package com.putzwirk.mapmakermusic.block;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

public final class MusicRuleEvaluator {

	private MusicRuleEvaluator() {
	}

	public static boolean matches(MusicQueue queue, ServerPlayer player) {
		return matchesNode(queue.getRuleRoot(), player);
	}

	public static boolean matchesNode(Object node, ServerPlayer player) {
		if (node instanceof ConditionGroup group) {
			if (group.getKids().isEmpty()) {
				return true;
			}
			if (group.getOp() == ConditionGroup.Op.ANY) {
				for (Object kid : group.getKids()) {
					if (matchesNode(kid, player)) {
						return true;
					}
				}
				return false;
			}
			for (Object kid : group.getKids()) {
				if (!matchesNode(kid, player)) {
					return false;
				}
			}
			return true;
		}
		return matches((MusicCondition) node, player);
	}

	public static boolean matches(MusicCondition condition, ServerPlayer player) {
		return switch (condition.getType()) {
			case TIME -> matchTime(condition, player);
			case WEATHER -> matchWeather(condition, player);
			case SCOREBOARD -> matchScore(condition, player);
			case PLAYER_HEALTH -> inRange(player.getHealth(), condition);
			case PLAYER_HUNGER -> inRange(player.getFoodData().getFoodLevel(), condition);
			case ENTITY_ALIVE -> matchBoss(condition, player);
			case IN_BIOME -> matchBiome(condition, player);
			case POS_X -> inRange(player.getX(), condition);
			case POS_Y -> inRange(player.getY(), condition);
			case POS_Z -> inRange(player.getZ(), condition);
		};
	}

	private static boolean matchTime(MusicCondition condition, ServerPlayer player) {
		long time = player.level().getDayTime() % 24000L;
		String mode = condition.getText().toLowerCase(Locale.ROOT);
		if (mode.equals("day")) {
			return time < 12000L;
		}
		if (mode.equals("night")) {
			return time >= 12000L;
		}
		return time >= condition.getMin() && time <= condition.getMax();
	}

	private static boolean matchWeather(MusicCondition condition, ServerPlayer player) {
		String mode = condition.getText().toLowerCase(Locale.ROOT);
		if (mode.equals("rain")) {
			return player.level().isRaining();
		}
		if (mode.equals("thunder")) {
			return player.level().isThundering();
		}
		return !player.level().isRaining();
	}

	private static boolean matchScore(MusicCondition condition, ServerPlayer player) {
		Scoreboard scoreboard = player.level().getScoreboard();
		Objective objective = scoreboard.getObjective(condition.getText());
		if (objective == null) {
			return false;
		}
		int score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).getScore();
		return inRange(score, condition);
	}

	private static boolean matchBoss(MusicCondition condition, ServerPlayer player) {
		Optional<EntityType<?>> type = EntityType.byString(condition.getText());
		if (type.isEmpty() || player.getServer() == null) {
			return false;
		}

		boolean alive = false;
		for (ServerLevel level : player.getServer().getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity.getType() == type.get() && entity.isAlive()) {
					alive = true;
					break;
				}
			}
			if (alive) {
				break;
			}
		}

		return alive == (condition.getMin() >= 0.5);
	}

	private static boolean matchBiome(MusicCondition condition, ServerPlayer player) {
		Holder<Biome> biome = player.level().getBiome(player.blockPosition());
		String id = biome.unwrapKey().map(key -> key.location().toString()).orElse("");
		return id.equalsIgnoreCase(condition.getText());
	}

	private static boolean inRange(double value, MusicCondition condition) {
		return value >= condition.getMin() && value <= condition.getMax();
	}
}
