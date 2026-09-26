package com.putzwirk.mapmakermusic.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

public class MusicBlockEntityClampTest {

	private static MusicBlockEntity loaded(CompoundTag tag) {
		MusicBlockEntity be = new MusicBlockEntity(null, BlockPos.ZERO, null);
		be.load(tag);
		return be;
	}

	@Test
	public void volumeClamped() {
		CompoundTag high = new CompoundTag();
		high.putInt("Volume", 9999);
		assertEquals(200, loaded(high).getVolume());

		CompoundTag low = new CompoundTag();
		low.putInt("Volume", -50);
		assertEquals(0, loaded(low).getVolume());

		assertEquals(100, loaded(new CompoundTag()).getVolume());
	}

	@Test
	public void pitchClamped() {
		CompoundTag high = new CompoundTag();
		high.putFloat("Pitch", 99f);
		assertEquals(4.0f, loaded(high).getPitch(), 0.0001f);

		CompoundTag low = new CompoundTag();
		low.putFloat("Pitch", -2f);
		assertEquals(0.1f, loaded(low).getPitch(), 0.0001f);

		CompoundTag nan = new CompoundTag();
		nan.putFloat("Pitch", Float.NaN);
		assertEquals(1.0f, loaded(nan).getPitch(), 0.0001f);

		assertEquals(1.0f, loaded(new CompoundTag()).getPitch(), 0.0001f);
	}

	@Test
	public void radiusClamped() {
		CompoundTag high = new CompoundTag();
		high.putInt("Radius", 99999);
		assertEquals(256, loaded(high).getRadius());

		CompoundTag zero = new CompoundTag();
		zero.putInt("Radius", 0);
		assertEquals(1, loaded(zero).getRadius());

		CompoundTag negative = new CompoundTag();
		negative.putInt("Radius", -10);
		assertEquals(1, loaded(negative).getRadius());

		assertEquals(16, loaded(new CompoundTag()).getRadius());
	}

	@Test
	public void legacyFormatStillLoads() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("ActivationType", 1);
		ListTag cues = new ListTag();
		cues.add(new MusicQueue().save());
		tag.put("Cues", cues);

		MusicBlockEntity be = loaded(tag);
		assertEquals(MusicBlockEntity.TriggerMode.CHAIN, be.getTriggerMode());
		assertTrue(be.isAreaGate());
		assertEquals(1, be.getQueues().size());
	}

	@Test
	public void legacyFormatClampsBadValues() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("ActivationType", 0);
		tag.putInt("Volume", Integer.MAX_VALUE);
		tag.putFloat("Pitch", Float.POSITIVE_INFINITY);
		tag.putInt("Radius", Integer.MIN_VALUE);

		MusicBlockEntity be = loaded(tag);
		assertEquals(MusicBlockEntity.TriggerMode.IMPULSE, be.getTriggerMode());
		assertEquals(200, be.getVolume());
		assertEquals(4.0f, be.getPitch(), 0.0001f);
		assertEquals(1, be.getRadius());
	}
}
