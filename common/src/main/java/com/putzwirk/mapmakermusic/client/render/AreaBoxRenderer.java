package com.putzwirk.mapmakermusic.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.putzwirk.mapmakermusic.block.ModBlocks;
import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicBlockTicker;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AreaBoxRenderer {

	private static final int SCAN_CHUNK_RADIUS = 4;
	private static final int REFRESH_INTERVAL_TICKS = 20;
	private static final int MAX_BOXES = 128;
	private static final double PREVIEW_GROW = 0.02;

	private static BlockPos wandSelection;
	private static long lastRefreshGameTime = -1L;
	private static final List<Entry> cachedBoxes = new ArrayList<>();

	private AreaBoxRenderer() {
	}

	public static void setWandSelection(BlockPos pos) {
		wandSelection = pos == null ? null : pos.immutable();
	}

	public static void render(PoseStack poseStack, Vec3 camPos, MultiBufferSource buffers) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		boolean holding = isWandInHand(client.player);
		if (!holding && wandSelection == null) {
			cachedBoxes.clear();
			return;
		}

		refreshCache(client);

		poseStack.pushPose();
		poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

		for (Entry entry : cachedBoxes) {
			AABB grown = entry.area.inflate(growFor(entry.pos));
			float[] rgb = colorFor(entry.pos);
			LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), grown, rgb[0], rgb[1], rgb[2], 1f);
		}

		AABB preview = selectionPreview(client);
		if (preview != null) {
			AABB grownPreview = preview.inflate(PREVIEW_GROW);
			LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), grownPreview, 1f, 1f, 1f, 1f);
		}

		poseStack.popPose();

		if (buffers instanceof MultiBufferSource.BufferSource immediate) {
			immediate.endBatch(RenderType.lines());
		}
	}

	private static boolean isWandInHand(Player player) {
		return player.getMainHandItem().is(ModBlocks.MUSIC_BLOCK_ITEM.get())
				|| player.getOffhandItem().is(ModBlocks.MUSIC_BLOCK_ITEM.get());
	}

	private static void refreshCache(Minecraft client) {
		long gameTime = client.level.getGameTime();
		if (lastRefreshGameTime >= 0L && gameTime - lastRefreshGameTime < REFRESH_INTERVAL_TICKS) {
			return;
		}
		lastRefreshGameTime = gameTime;
		cachedBoxes.clear();

		ChunkPos center = client.player.chunkPosition();
		for (int dx = -SCAN_CHUNK_RADIUS; dx <= SCAN_CHUNK_RADIUS; dx++) {
			for (int dz = -SCAN_CHUNK_RADIUS; dz <= SCAN_CHUNK_RADIUS; dz++) {
				LevelChunk chunk = client.level.getChunkSource().getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (cachedBoxes.size() >= MAX_BOXES) {
						return;
					}
					if (be instanceof MusicBlockEntity musicBe
							&& musicBe.getActivationType() == MusicBlockEntity.ActivationType.AREA) {
						cachedBoxes.add(new Entry(musicBe.getBlockPos().immutable(), MusicBlockTicker.areaOf(musicBe)));
					}
				}
			}
		}
	}

	private static AABB selectionPreview(Minecraft client) {
		if (wandSelection == null) {
			return null;
		}
		HitResult hit = client.hitResult;
		if (hit instanceof BlockHitResult blockHit) {
			BlockPos pos2 = blockHit.getBlockPos();
			return new AABB(
					Math.min(wandSelection.getX(), pos2.getX()),
					Math.min(wandSelection.getY(), pos2.getY()),
					Math.min(wandSelection.getZ(), pos2.getZ()),
					Math.max(wandSelection.getX(), pos2.getX()) + 1.0,
					Math.max(wandSelection.getY(), pos2.getY()) + 1.0,
					Math.max(wandSelection.getZ(), pos2.getZ()) + 1.0);
		}
		return new AABB(wandSelection);
	}

	private static double growFor(BlockPos pos) {
		long hash = pos.asLong() * 0x9E3779B97F4A7C15L;
		return 0.01 + ((hash >>> 8) % 16) * 0.0015;
	}

	private static float[] colorFor(BlockPos pos) {		long hash = pos.asLong() * 0x9E3779B97F4A7C15L;
		float hue = (float) (((hash >>> 16) % 360 + 360) % 360) / 360f;
		int packed = Color.HSBtoRGB(hue, 0.85f, 1f);
		return new float[]{
				((packed >> 16) & 0xFF) / 255f,
				((packed >> 8) & 0xFF) / 255f,
				(packed & 0xFF) / 255f
		};
	}

	private static final class Entry {
		final BlockPos pos;
		final AABB area;

		Entry(BlockPos pos, AABB area) {
			this.pos = pos;
			this.area = area;
		}
	}
}
