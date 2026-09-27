package com.putzwirk.mapmakermusic.block.condition;

import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public final class InBiomeConditionKind implements ConditionKind {

	public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "in_biome");
	public static final String BIOME_KEY = "biome";

	@Override
	public ResourceLocation id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "In biome";
	}

	@Override
	public boolean evaluate(MusicCondition condition, ConditionContext ctx) {
		Holder<Biome> biome = ctx.player().level().getBiome(ctx.player().blockPosition());
		String id = biome.unwrapKey().map(key -> key.location().toString()).orElse("");
		return id.equalsIgnoreCase(ConditionParams.str(condition.params(), BIOME_KEY, ""));
	}

	@Override
	public String describe(MusicCondition condition) {
		return "Biome " + ConditionParams.pretty(ConditionParams.str(condition.params(), BIOME_KEY, ""));
	}

	@Override
	public MusicCondition newDefault() {
		MusicCondition condition = new MusicCondition(ID);
		condition.params().putString(BIOME_KEY, "minecraft:plains");
		return condition;
	}

	@Override
	public List<FieldSpec> editorFields() {
		return List.of(FieldSpec.entityId(BIOME_KEY, "biome id", "biome"));
	}
}
