package com.putzwirk.mapmakermusic.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class MusicQueue {

	public enum Channel {
		MUSIC,
		SOUND
	}

	public static class PlaylistItem {
		public static final String STOP_TRACK = "STOP";

		public static boolean isStop(String track) {
			return track != null && track.equalsIgnoreCase(STOP_TRACK);
		}

		private String track = "";
		private Integer volume;
		private Float pitch;

		public PlaylistItem() {
		}

		public PlaylistItem(String track) {
			this.track = track == null ? "" : track;
		}

		public PlaylistItem(String track, Integer volume, Float pitch) {
			this.track = track == null ? "" : track;
			this.volume = volume;
			this.pitch = pitch;
		}

		public String getTrack() {
			return track;
		}

		public void setTrack(String track) {
			this.track = track == null ? "" : track;
		}

		public Integer getVolume() {
			return volume;
		}

		public void setVolume(Integer volume) {
			this.volume = volume;
		}

		public Float getPitch() {
			return pitch;
		}

		public void setPitch(Float pitch) {
			this.pitch = pitch;
		}

		public boolean isStop() {
			return isStop(track);
		}

		public PlaylistItem copy() {
			return new PlaylistItem(track, volume, pitch);
		}

		public CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putString("Name", track);
			if (volume != null) {
				tag.putInt("Volume", volume);
			}
			if (pitch != null) {
				tag.putFloat("Pitch", pitch);
			}
			return tag;
		}

		public static PlaylistItem load(CompoundTag tag) {
			PlaylistItem item = new PlaylistItem(tag.getString("Name"));
			if (tag.contains("Volume")) {
				item.volume = tag.getInt("Volume");
			}
			if (tag.contains("Pitch")) {
				item.pitch = tag.getFloat("Pitch");
			}
			return item;
		}
	}

	private final List<PlaylistItem> tracks = new ArrayList<>();
	private Channel channel = Channel.MUSIC;
	private boolean channelExplicit = false;
	private boolean fadeIn = true;
	private boolean fadeOut = true;
	private boolean loop = true;
	private final ConditionGroup ruleRoot = new ConditionGroup();

	public List<PlaylistItem> getTracks() {
		return tracks;
	}

	public Channel getChannel() {
		return channel;
	}

	public void setChannel(Channel channel) {
		this.channel = channel;
		this.channelExplicit = true;
	}

	public boolean isChannelExplicit() {
		return channelExplicit;
	}

	public void setTracks(List<String> value) {
		tracks.clear();
		if (value != null) {
			for (String track : value) {
				tracks.add(new PlaylistItem(track));
			}
		}
	}

	public List<String> getTrackNames() {
		List<String> names = new ArrayList<>(tracks.size());
		for (PlaylistItem item : tracks) {
			names.add(item.getTrack());
		}
		return names;
	}

	public boolean isFadeIn() {
		return fadeIn;
	}

	public void setFadeIn(boolean fadeIn) {
		this.fadeIn = fadeIn;
	}

	public boolean isFadeOut() {
		return fadeOut;
	}

	public void setFadeOut(boolean fadeOut) {
		this.fadeOut = fadeOut;
	}

	public boolean isLoop() {
		return loop;
	}

	public void setLoop(boolean loop) {
		this.loop = loop;
	}

	public ConditionGroup getRuleRoot() {
		return ruleRoot;
	}

	public boolean isPlaylist() {
		return tracks.size() > 1;
	}

	public String getPrimaryTrack() {
		return tracks.isEmpty() ? "" : tracks.get(0).getTrack();
	}

	public MusicQueue copy() {
		MusicQueue copy = new MusicQueue();
		for (PlaylistItem item : tracks) {
			copy.tracks.add(item.copy());
		}
		copy.channel = channel;
		copy.channelExplicit = channelExplicit;
		copy.fadeIn = fadeIn;
		copy.fadeOut = fadeOut;
		copy.loop = loop;
		copy.ruleRoot.getKids().clear();
		copy.ruleRoot.getKids().addAll(ruleRoot.copy().getKids());
		copy.ruleRoot.setOp(ruleRoot.getOp());
		return copy;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();

		ListTag trackList = new ListTag();
		for (PlaylistItem item : tracks) {
			trackList.add(item.save());
		}
		tag.put("Tracks", trackList);

		tag.putInt("Channel", channel.ordinal());
		tag.putBoolean("FadeIn", fadeIn);
		tag.putBoolean("FadeOut", fadeOut);
		tag.putBoolean("Loop", loop);

		tag.put("RuleRoot", ruleRoot.save());

		return tag;
	}

	public static MusicQueue load(CompoundTag tag) {
		MusicQueue queue = new MusicQueue();

		ListTag trackList = tag.getList("Tracks", Tag.TAG_COMPOUND);
		for (int i = 0; i < trackList.size(); i++) {
			queue.tracks.add(PlaylistItem.load(trackList.getCompound(i)));
		}

		Integer legacyVolume = tag.contains("Volume") ? tag.getInt("Volume") : null;
		Float legacyPitch = tag.contains("Pitch") ? tag.getFloat("Pitch") : null;
		if (legacyVolume != null || legacyPitch != null) {
			for (PlaylistItem item : queue.tracks) {
				if (item.getVolume() == null) {
					item.setVolume(legacyVolume);
				}
				if (item.getPitch() == null) {
					item.setPitch(legacyPitch);
				}
			}
		}

		if (tag.contains("Channel")) {
			int channel = Math.max(0, Math.min(Channel.values().length - 1, tag.getInt("Channel")));
			queue.channel = Channel.values()[channel];
			queue.channelExplicit = true;
		}

		queue.fadeIn = !tag.contains("FadeIn") || tag.getBoolean("FadeIn");
		queue.fadeOut = !tag.contains("FadeOut") || tag.getBoolean("FadeOut");

		if (tag.contains("Loop")) {
			queue.loop = tag.getBoolean("Loop");
		} else if (tag.contains("PlaylistMode")) {
			queue.loop = tag.getInt("PlaylistMode") != 0;
		} else {
			queue.loop = true;
		}

		if (tag.contains("RuleRoot")) {
			ConditionGroup loaded = ConditionGroup.load(tag.getCompound("RuleRoot"));
			queue.ruleRoot.getKids().clear();
			queue.ruleRoot.getKids().addAll(loaded.getKids());
			queue.ruleRoot.setOp(loaded.getOp());
		} else {
			ListTag conditionList = tag.getList("Conditions", Tag.TAG_COMPOUND);
			for (int i = 0; i < conditionList.size(); i++) {
				queue.ruleRoot.getKids().add(MusicCondition.load(conditionList.getCompound(i)));
			}
			if (tag.contains("ConditionMatch")) {
				try {
					queue.ruleRoot.setOp(ConditionGroup.Op.valueOf(tag.getString("ConditionMatch")));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}

		return queue;
	}
}
