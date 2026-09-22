package com.putzwirk.mapmakermusic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;

public class MusicBlockEntity extends BlockEntity {

	public enum ActivationType {
		REDSTONE,
		AREA
	}

	public enum AudioType {
		MUSIC,
		SOUND
	}

	public enum PlaybackMode {
		GLOBAL,
		POSITIONAL
	}

	private ActivationType activationType = ActivationType.REDSTONE;
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

	public ActivationType getActivationType() {
		return activationType;
	}

	public void setActivationType(ActivationType activationType) {
		this.activationType = activationType;
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
		if (tag.contains("ActivationType")) {
			this.activationType = ActivationType.values()[Math.max(0, Math.min(ActivationType.values().length - 1, tag.getInt("ActivationType")))];
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
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putInt("ActivationType", this.activationType.ordinal());
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
