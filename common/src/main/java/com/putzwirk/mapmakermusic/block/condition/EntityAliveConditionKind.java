package com.putzwirk.mapmakermusic.block.condition;

import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.block.EntityCountCache;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

public final class EntityAliveConditionKind implements ConditionKind {

	public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "entity_alive");
	public static final String ENTITY_KEY = "id";
	public static final String OP_KEY = "op";
	public static final String THRESHOLD_KEY = "threshold";
	public static final String TAG_KEY = "tag";
	private static final List<String> MODES = List.of("at least", "at most", "exactly");
	private static final EntityCountCache COUNT_CACHE = new EntityCountCache();

	@Override
	public ResourceLocation id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Entity count";
	}

	@Override
	public boolean needsAreaGate() {
		return true;
	}

	@Override
	public boolean evaluate(MusicCondition condition, ConditionContext ctx) {
		MinecraftServer server = ctx.player().getServer();
		if (server == null) {
			return false;
		}
		String id = ConditionParams.str(condition.params(), ENTITY_KEY, "").trim();
		String tag = ConditionParams.str(condition.params(), TAG_KEY, "").trim();
		Optional<EntityType<?>> type = EntityType.byString(id);
		int count;
		if (type.isPresent()) {
			count = countFor(type.get(), tag, server, ctx.area(), ctx.level());
		} else if (!tag.isEmpty()) {
			count = countFor(null, tag, server, ctx.area(), ctx.level());
		} else {
			count = 0;
		}
		return matchesCount(modeOf(condition), ConditionParams.dbl(condition.params(), THRESHOLD_KEY, 1), count);
	}

	@Override
	public String describe(MusicCondition condition) {
		String who = ConditionParams.pretty(ConditionParams.str(condition.params(), ENTITY_KEY, ""));
		String want = ConditionParams.str(condition.params(), TAG_KEY, "").trim();
		if (!want.isEmpty()) {
			who = (who.isEmpty() ? "" : who + " ") + "#" + want;
		}
		return who + " " + countSymbol(modeOf(condition)) + " "
				+ ConditionParams.format(ConditionParams.dbl(condition.params(), THRESHOLD_KEY, 1));
	}

	@Override
	public MusicCondition newDefault() {
		MusicCondition condition = new MusicCondition(ID);
		condition.params().putString(ENTITY_KEY, "minecraft:wither");
		condition.params().putDouble(THRESHOLD_KEY, 1);
		return condition;
	}

	@Override
	public List<FieldSpec> editorFields() {
		return List.of(
				FieldSpec.modeCycle(),
				FieldSpec.entityId(ENTITY_KEY, "entity id", "entity"),
				FieldSpec.number(THRESHOLD_KEY, "Count"),
				FieldSpec.text(TAG_KEY, "Tag", "scoreboard tag, optional"));
	}

	@Override
	public List<String> modes() {
		return MODES;
	}

	@Override
	public String modeOf(MusicCondition condition) {
		String op = ConditionParams.str(condition.params(), OP_KEY, "at least");
		return MODES.contains(op) ? op : "at least";
	}

	@Override
	public void cycleMode(MusicCondition condition) {
		String current = modeOf(condition);
		condition.params().putString(OP_KEY, MODES.get((MODES.indexOf(current) + 1) % MODES.size()));
	}

	public static boolean matchesCount(String op, double threshold, int count) {
		return switch (op) {
			case "at most" -> count <= threshold;
			case "exactly" -> count == threshold;
			default -> count >= threshold;
		};
	}

	private static String countSymbol(String op) {
		return switch (op) {
			case "at most" -> "<=";
			case "exactly" -> "==";
			default -> ">=";
		};
	}

	private static int countFor(EntityType<?> type, String tag, MinecraftServer server, AABB area, ServerLevel level) {
		if (area != null && level != null) {
			return level.getEntitiesOfClass(Entity.class, area, entity -> matchesEntity(entity, type, tag)).size();
		}
		String key = (type == null ? "" : EntityType.getKey(type).toString()) + "#" + tag;
		return COUNT_CACHE.get(server, server.getTickCount(), key, () -> scanCount(type, tag, server));
	}

	private static boolean matchesEntity(Entity entity, EntityType<?> type, String tag) {
		if (!entity.isAlive()) {
			return false;
		}
		if (type != null && entity.getType() != type) {
			return false;
		}
		return tag.isEmpty() || entity.getTags().contains(tag);
	}

	private static int scanCount(EntityType<?> type, String tag, MinecraftServer server) {
		int count = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (matchesEntity(entity, type, tag)) {
					count++;
				}
			}
		}
		return count;
	}
}
