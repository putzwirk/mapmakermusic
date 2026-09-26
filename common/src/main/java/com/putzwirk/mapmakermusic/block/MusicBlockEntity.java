package com.putzwirk.mapmakermusic.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;

public class MusicBlockEntity extends BlockEntity {

	@Deprecated
	public enum ActivationType {
		REDSTONE,
		AREA
	}

	public enum TriggerMode {
		IMPULSE,
		CHAIN
	}

	public enum AudioType {
		MUSIC,
		SOUND
	}

	public enum PlaybackMode {
		GLOBAL,
		POSITIONAL
	}

	private TriggerMode triggerMode = TriggerMode.IMPULSE;
	private boolean areaGate = false;
	private AudioType audioType = AudioType.MUSIC;
	private BlockPos pos1 = BlockPos.ZERO;
	private BlockPos pos2 = BlockPos.ZERO;
	private String audioTrack = "";
	private int volume = 100;
	private float pitch = 1.0f;
	private boolean loop = true;
	private boolean persistent = false;
	private boolean fadeIn = true;
	private boolean fadeOut = true;

	private PlaybackMode playbackMode = PlaybackMode.GLOBAL;
	private String listenerSelector = "@a";
	private BlockPos playbackPos = BlockPos.ZERO;
	private int radius = 16;

	private final List<MusicQueue> queues = new ArrayList<>();

	// Runtime state for Redstone edge triggering & Area tracking
	private boolean poweredLastTick = false;
	private boolean activeLastTick = false;

	public MusicBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.pos1 = pos;
		this.pos2 = pos;
		this.playbackPos = pos;
	}

	public MusicBlockEntity(BlockPos pos, BlockState state) {
		this(ModBlocks.MUSIC_BLOCK_ENTITY_TYPE.get(), pos, state);
	}

	@Deprecated
	public ActivationType getActivationType() {
		if (triggerMode == TriggerMode.CHAIN) {
			return ActivationType.AREA;
		}
		return ActivationType.REDSTONE;
	}

	@Deprecated
	public void setActivationType(ActivationType activationType) {
		if (activationType == ActivationType.AREA) {
			this.triggerMode = TriggerMode.CHAIN;
			this.areaGate = true;
		} else {
			this.triggerMode = TriggerMode.IMPULSE;
			this.areaGate = false;
		}
		setChanged();
	}

	public TriggerMode getTriggerMode() {
		return triggerMode;
	}

	public void setTriggerMode(TriggerMode triggerMode) {
		this.triggerMode = triggerMode == null ? TriggerMode.IMPULSE : triggerMode;
		setChanged();
	}

	public boolean isAreaGate() {
		return areaGate;
	}

	public void setAreaGate(boolean areaGate) {
		this.areaGate = areaGate;
		setChanged();
	}

	public AudioType getAudioType() {
		return audioType;
	}

	public void setAudioType(AudioType audioType) {
		this.audioType = audioType;
		setChanged();
	}

	public BlockPos getPos1() {
		return pos1;
	}

	public void setPos1(BlockPos pos1) {
		this.pos1 = pos1;
		setChanged();
	}

	public BlockPos getPos2() {
		return pos2;
	}

	public void setPos2(BlockPos pos2) {
		this.pos2 = pos2;
		setChanged();
	}

	public String getAudioTrack() {
		return audioTrack;
	}

	public void setAudioTrack(String audioTrack) {
		this.audioTrack = audioTrack;
		setChanged();
	}

	public int getVolume() {
		return volume;
	}

	public void setVolume(int volume) {
		this.volume = volume;
		setChanged();
	}

	public float getPitch() {
		return pitch;
	}

	public void setPitch(float pitch) {
		this.pitch = pitch;
		setChanged();
	}

	public boolean isLoop() {
		return loop;
	}

	public void setLoop(boolean loop) {
		this.loop = loop;
		setChanged();
	}

	public boolean isPersistent() {
		return persistent;
	}

	public void setPersistent(boolean persistent) {
		this.persistent = persistent;
		setChanged();
	}

	public boolean isFadeIn() {
		return fadeIn;
	}

	public void setFadeIn(boolean fadeIn) {
		this.fadeIn = fadeIn;
		setChanged();
	}

	public boolean isFadeOut() {
		return fadeOut;
	}

	public void setFadeOut(boolean fadeOut) {
		this.fadeOut = fadeOut;
		setChanged();
	}

	public PlaybackMode getPlaybackMode() {
		return playbackMode;
	}

	public void setPlaybackMode(PlaybackMode playbackMode) {
		this.playbackMode = playbackMode;
		setChanged();
	}

	public String getListenerSelector() {
		return listenerSelector;
	}

	public void setListenerSelector(String listenerSelector) {
		this.listenerSelector = listenerSelector;
		setChanged();
	}

	public BlockPos getPlaybackPos() {
		return playbackPos;
	}

	public void setPlaybackPos(BlockPos playbackPos) {
		this.playbackPos = playbackPos;
		setChanged();
	}

	public int getRadius() {
		return radius;
	}

	public void setRadius(int radius) {
		this.radius = radius;
		setChanged();
	}

	public List<MusicQueue> getQueues() {
		return queues;
	}

	public void setQueues(List<MusicQueue> value) {
		queues.clear();
		if (value != null) {
			queues.addAll(value);
		}
		setChanged();
	}

	public void addQueue(MusicQueue queue) {
		queues.add(queue);
		setChanged();
	}

	public void setQueue(int index, MusicQueue queue) {
		if (index >= 0 && index < queues.size()) {
			queues.set(index, queue);
			setChanged();
		}
	}

	public void removeQueue(int index) {
		if (index >= 0 && index < queues.size()) {
			queues.remove(index);
			setChanged();
		}
	}

	public void moveQueue(int index, int delta) {
		int target = index + delta;
		if (index >= 0 && index < queues.size() && target >= 0 && target < queues.size()) {
			MusicQueue queue = queues.remove(index);
			queues.add(target, queue);
			setChanged();
		}
	}

	public MusicQueue findMatchingQueue(ServerPlayer player) {
		for (MusicQueue queue : queues) {
			if (!queue.getTracks().isEmpty() && MusicRuleEvaluator.matches(queue, player)) {
				return queue;
			}
		}
		return null;
	}

	public boolean isPoweredLastTick() {
		return poweredLastTick;
	}

	public void setPoweredLastTick(boolean poweredLastTick) {
		this.poweredLastTick = poweredLastTick;
	}

	public boolean isActiveLastTick() {
		return activeLastTick;
	}

	public void setActiveLastTick(boolean activeLastTick) {
		this.activeLastTick = activeLastTick;
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		if (tag.contains("TriggerMode")) {
			try {
				this.triggerMode = TriggerMode.valueOf(tag.getString("TriggerMode"));
			} catch (IllegalArgumentException ignored) {
				this.triggerMode = TriggerMode.IMPULSE;
			}
			this.areaGate = tag.getBoolean("AreaGate");
		} else if (tag.contains("ActivationType")) {
			int legacy = Math.max(0, Math.min(ActivationType.values().length - 1, tag.getInt("ActivationType")));
			if (ActivationType.values()[legacy] == ActivationType.AREA) {
				this.triggerMode = TriggerMode.CHAIN;
				this.areaGate = true;
			} else {
				this.triggerMode = TriggerMode.IMPULSE;
				this.areaGate = false;
			}
		}
		if (tag.contains("AudioType")) {
			this.audioType = AudioType.values()[Math.max(0, Math.min(AudioType.values().length - 1, tag.getInt("AudioType")))];
		}
		if (tag.contains("Pos1")) {
			this.pos1 = NbtUtils.readBlockPos(tag.getCompound("Pos1"));
		}
		if (tag.contains("Pos2")) {
			this.pos2 = NbtUtils.readBlockPos(tag.getCompound("Pos2"));
		}
		this.audioTrack = tag.getString("AudioTrack");
		this.volume = tag.contains("Volume") ? tag.getInt("Volume") : 100;
		this.pitch = tag.contains("Pitch") ? tag.getFloat("Pitch") : 1.0f;
		this.loop = !tag.contains("Loop") || tag.getBoolean("Loop");
		this.persistent = tag.getBoolean("Persistent");
		this.fadeIn = !tag.contains("FadeIn") || tag.getBoolean("FadeIn");
		this.fadeOut = !tag.contains("FadeOut") || tag.getBoolean("FadeOut");

		if (tag.contains("PlaybackMode")) {
			this.playbackMode = PlaybackMode.values()[Math.max(0, Math.min(PlaybackMode.values().length - 1, tag.getInt("PlaybackMode")))];
		}
		this.listenerSelector = tag.contains("ListenerSelector") ? tag.getString("ListenerSelector") : "@a";
		if (tag.contains("PlaybackPos")) {
			this.playbackPos = NbtUtils.readBlockPos(tag.getCompound("PlaybackPos"));
		} else {
			this.playbackPos = this.worldPosition;
		}
		this.radius = tag.contains("Radius") ? tag.getInt("Radius") : 16;

		this.queues.clear();
		ListTag queueList = tag.getList("Queues", Tag.TAG_COMPOUND);
		if (queueList.isEmpty() && tag.contains("Cues")) {
			queueList = tag.getList("Cues", Tag.TAG_COMPOUND);
		}
		for (int i = 0; i < queueList.size(); i++) {
			this.queues.add(MusicQueue.load(queueList.getCompound(i)));
		}
		if (this.queues.isEmpty() && !this.audioTrack.isEmpty()) {
			MusicQueue legacy = new MusicQueue();
			legacy.getTracks().add(new MusicQueue.PlaylistItem(this.audioTrack));
			this.queues.add(legacy);
		}
		if (this.audioType == AudioType.SOUND) {
			for (MusicQueue queue : this.queues) {
				if (!queue.isChannelExplicit()) {
					queue.setChannel(MusicQueue.Channel.SOUND);
				}
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putString("TriggerMode", this.triggerMode.name());
		tag.putBoolean("AreaGate", this.areaGate);
		tag.putInt("AudioType", this.audioType.ordinal());
		tag.put("Pos1", NbtUtils.writeBlockPos(this.pos1));
		tag.put("Pos2", NbtUtils.writeBlockPos(this.pos2));
		tag.putString("AudioTrack", this.audioTrack);
		tag.putInt("Volume", this.volume);
		tag.putFloat("Pitch", this.pitch);
		tag.putBoolean("Loop", this.loop);
		tag.putBoolean("Persistent", this.persistent);
		tag.putBoolean("FadeIn", this.fadeIn);
		tag.putBoolean("FadeOut", this.fadeOut);

		tag.putInt("PlaybackMode", this.playbackMode.ordinal());
		tag.putString("ListenerSelector", this.listenerSelector);
		tag.put("PlaybackPos", NbtUtils.writeBlockPos(this.playbackPos));
		tag.putInt("Radius", this.radius);

		ListTag queueList = new ListTag();
		for (MusicQueue queue : this.queues) {
			queueList.add(queue.save());
		}
		tag.put("Queues", queueList);
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = new CompoundTag();
		saveAdditional(tag);
		return tag;
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
