package com.putzwirk.mapmakermusic.block.condition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

public record ConditionContext(ServerPlayer player, BlockPos boxPos, AABB area, ServerLevel level) {
}
