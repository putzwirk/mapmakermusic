package com.putzwirk.mapmakermusic.block;

import com.putzwirk.mapmakermusic.Constants;
import com.putzwirk.mapmakermusic.block.condition.ConditionKind;
import com.putzwirk.mapmakermusic.block.condition.ConditionKindRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class MusicCondition {

	public static final ResourceLocation UNKNOWN_ID = new ResourceLocation(Constants.MOD_ID, "unknown");

	private ResourceLocation kindId;
	private CompoundTag params = new CompoundTag();

	public MusicCondition(ResourceLocation kindId) {
		this.kindId = kindId == null ? UNKNOWN_ID : kindId;
	}

	public ResourceLocation getKindId() {
		return kindId;
	}

	public void setKindId(ResourceLocation kindId) {
		this.kindId = kindId == null ? UNKNOWN_ID : kindId;
	}

	public ConditionKind kind() {
		return ConditionKindRegistry.get(kindId);
	}

	public CompoundTag params() {
		return params;
	}

	public MusicCondition copy() {
		MusicCondition copy = new MusicCondition(kindId);
		copy.params = params.copy();
		return copy;
	}

	public String describe() {
		ConditionKind kind = kind();
		return kind == null ? "Unknown condition" : kind.describe(this);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Kind", kindId.toString());
		tag.put("Params", params.copy());
		return tag;
	}

	public static MusicCondition load(CompoundTag tag) {
		ResourceLocation id = ResourceLocation.tryParse(tag.getString("Kind"));
		MusicCondition condition = new MusicCondition(id == null ? UNKNOWN_ID : id);
		if (tag.contains("Params")) {
			condition.params = tag.getCompound("Params").copy();
		}
		return condition;
	}
}
