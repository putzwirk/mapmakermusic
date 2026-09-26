package com.putzwirk.mapmakermusic.block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class PlaybackSaveData extends SavedData {
	public static final String ID = "mapmakermusic_playback";

	public static final class Entry {
		public final int queueIndex;
		public final int trackIndex;
		public final long startedTick;

		public Entry(int queueIndex, int trackIndex, long startedTick) {
			this.queueIndex = queueIndex;
			this.trackIndex = trackIndex;
			this.startedTick = startedTick;
		}
	}

	private final Map<String, Map<UUID, Entry>> boxes = new HashMap<>();

	public static String boxId(String dimensionId, long pos) {
		return dimensionId + "|" + pos;
	}

	public static PlaybackSaveData get(MinecraftServer server) {
		ServerLevel level = server.overworld();
		return level.getDataStorage().computeIfAbsent(PlaybackSaveData::load, PlaybackSaveData::new, ID);
	}

	public Entry get(String boxId, UUID player) {
		Map<UUID, Entry> players = boxes.get(boxId);
		return players == null ? null : players.get(player);
	}

	public void put(String boxId, UUID player, Entry entry) {
		boxes.computeIfAbsent(boxId, k -> new HashMap<>()).put(player, entry);
		setDirty();
	}

	public void remove(String boxId, UUID player) {
		Map<UUID, Entry> players = boxes.get(boxId);
		if (players != null) {
			players.remove(player);
			if (players.isEmpty()) {
				boxes.remove(boxId);
			}
			setDirty();
		}
	}

	public void removeBox(String boxId) {
		if (boxes.remove(boxId) != null) {
			setDirty();
		}
	}

	public void removePlayer(UUID player) {
		boolean changed = false;
		var it = boxes.entrySet().iterator();
		while (it.hasNext()) {
			Map<UUID, Entry> players = it.next().getValue();
			if (players.remove(player) != null) {
				changed = true;
			}
			if (players.isEmpty()) {
				it.remove();
			}
		}
		if (changed) {
			setDirty();
		}
	}

	public static PlaybackSaveData load(CompoundTag tag) {
		PlaybackSaveData data = new PlaybackSaveData();
		ListTag boxList = tag.getList("Boxes", Tag.TAG_COMPOUND);
		for (int i = 0; i < boxList.size(); i++) {
			CompoundTag boxTag = boxList.getCompound(i);
			String boxId = boxTag.getString("Id");
			Map<UUID, Entry> players = new HashMap<>();
			ListTag playerList = boxTag.getList("Players", Tag.TAG_COMPOUND);
			for (int j = 0; j < playerList.size(); j++) {
				CompoundTag playerTag = playerList.getCompound(j);
				try {
					UUID uuid = playerTag.getUUID("UUID");
					players.put(uuid, new Entry(playerTag.getInt("Queue"), playerTag.getInt("Track"), playerTag.getLong("Started")));
				} catch (IllegalArgumentException ignored) {
				}
			}
			if (!boxId.isEmpty() && !players.isEmpty()) {
				data.boxes.put(boxId, players);
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag boxList = new ListTag();
		for (Map.Entry<String, Map<UUID, Entry>> box : boxes.entrySet()) {
			CompoundTag boxTag = new CompoundTag();
			boxTag.putString("Id", box.getKey());
			ListTag playerList = new ListTag();
			for (Map.Entry<UUID, Entry> player : box.getValue().entrySet()) {
				CompoundTag playerTag = new CompoundTag();
				playerTag.putUUID("UUID", player.getKey());
				playerTag.putInt("Queue", player.getValue().queueIndex);
				playerTag.putInt("Track", player.getValue().trackIndex);
				playerTag.putLong("Started", player.getValue().startedTick);
				playerList.add(playerTag);
			}
			boxTag.put("Players", playerList);
			boxList.add(boxTag);
		}
		tag.put("Boxes", boxList);
		return tag;
	}
}
