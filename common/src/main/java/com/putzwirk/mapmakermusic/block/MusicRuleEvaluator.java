package com.putzwirk.mapmakermusic.block;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

public final class MusicRuleEvaluator {

	private static final EntityAliveCache ALIVE_CACHE = new EntityAliveCache();

	private MusicRuleEvaluator() {
	}

	public static boolean matches(MusicQueue queue, ServerPlayer player, BlockPos boxPos) {
		return matchesNode(queue.getRuleRoot(), player, boxPos);
	}

	public static boolean matchesNode(Object node, ServerPlayer player, BlockPos boxPos) {
		if (node instanceof ConditionGroup group) {
			if (group.getKids().isEmpty()) {
				return true;
			}
			if (group.getOp() == ConditionGroup.Op.ANY) {
				for (Object kid : group.getKids()) {
					if (matchesNode(kid, player, boxPos)) {
						return true;
					}
				}
				return false;
			}
			for (Object kid : group.getKids()) {
				if (!matchesNode(kid, player, boxPos)) {
					return false;
				}
			}
			return true;
		}
		return matches((MusicCondition) node, player, boxPos);
	}

	public static boolean matches(MusicCondition condition, ServerPlayer player, BlockPos boxPos) {
		return switch (condition.getType()) {
			case TIME -> matchTime(condition, player);
			case WEATHER -> matchWeather(condition, player);
			case SCOREBOARD -> matchScore(condition, player);
			case PLAYER -> matchPlayer(condition, player, boxPos);
			case PLAYER_HEALTH -> inRange(player.getHealth(), condition);
			case PLAYER_HUNGER -> inRange(player.getFoodData().getFoodLevel(), condition);
			case ENTITY_ALIVE -> matchBoss(condition, player);
			case IN_BIOME -> matchBiome(condition, player);
			case COORDINATES -> matchCoordinates(condition, player);
		};
	}

	private static boolean matchPlayer(MusicCondition condition, ServerPlayer player, BlockPos boxPos) {
		String selector = condition.getText() == null ? "" : condition.getText().trim();
		if (selector.isEmpty() || selector.equals("@a")) {
			return true;
		}
		if (selector.equals("@p")) {
			ServerPlayer nearest = null;
			double nearestDist = Double.MAX_VALUE;
			Vec3 boxVec = Vec3.atCenterOf(boxPos);
			for (ServerPlayer other : player.serverLevel().players().stream().filter(p -> p instanceof ServerPlayer).map(p -> (ServerPlayer) p).toList()) {
				double dist = other.distanceToSqr(boxVec);
				if (dist < nearestDist) {
					nearestDist = dist;
					nearest = other;
				}
			}
			return nearest != null && nearest.getUUID().equals(player.getUUID());
		}
		return player.getScoreboardName().equalsIgnoreCase(selector) || player.getUUID().toString().equalsIgnoreCase(selector);
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
		MinecraftServer server = player.getServer();
		if (server == null) {
			return false;
		}
		String id = condition.getText() == null ? "" : condition.getText().trim();
		Optional<EntityType<?>> type = EntityType.byString(id);
		boolean alive = type.map(resolved -> {
			String key = EntityType.getKey(resolved).toString();
			return ALIVE_CACHE.get(server, server.getTickCount(), key, () -> scanAlive(server, resolved));
		}).orElse(false);
		return bossMatches(alive, condition);
	}

	static boolean bossMatches(boolean alive, MusicCondition condition) {
		return alive == (condition.getMin() >= 0.5);
	}

	private static boolean scanAlive(MinecraftServer server, EntityType<?> type) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity.getType() == type && entity.isAlive()) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean matchBiome(MusicCondition condition, ServerPlayer player) {
		Holder<Biome> biome = player.level().getBiome(player.blockPosition());
		String id = biome.unwrapKey().map(key -> key.location().toString()).orElse("");
		return id.equalsIgnoreCase(condition.getText());
	}

	private static boolean inRange(double value, MusicCondition condition) {
		return value >= condition.getMin() && value <= condition.getMax();
	}

	private static boolean matchCoordinates(MusicCondition condition, ServerPlayer player) {
		return axisMatches(player.getX(), condition.getBound(0), condition.getBound(1))
				&& axisMatches(player.getY(), condition.getBound(2), condition.getBound(3))
				&& axisMatches(player.getZ(), condition.getBound(4), condition.getBound(5));
	}

	private static boolean axisMatches(double value, double min, double max) {
		if (Double.isNaN(min) && Double.isNaN(max)) {
			return true;
		}
		if (Double.isNaN(min)) {
			min = max;
		}
		if (Double.isNaN(max)) {
			max = min;
		}
		return value >= min && value <= max;
	}
}
