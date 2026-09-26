package com.putzwirk.mapmakermusic.client.gui;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class MusicBlockScreen extends Screen {

	private static final int BG_WIDTH = 264;
	private static final int BG_HEIGHT = 232;
	private static final int FIELD_X = 88;
	private static final int FIELD_WIDTH = 164;

	public interface PacketSender {
		void sendUpdatePacket(BlockPos pos, net.minecraft.nbt.CompoundTag data);
	}

	private static PacketSender packetSender;

	public static void setPacketSender(PacketSender sender) {
		packetSender = sender;
	}

	static void sendUpdate(MusicBlockEntity block) {
		if (packetSender != null) {
			packetSender.sendUpdatePacket(block.getBlockPos(), block.getUpdateTag());
		}
	}

	private final MusicBlockEntity musicBlock;

	private MusicBlockEntity.TriggerMode triggerMode;
	private boolean areaGate;
	private MusicBlockEntity.AudioType audioType;
	private MusicBlockEntity.PlaybackMode playbackMode;

	private Button triggerButton;
	private Button gateButton;
	private Button playbackButton;
	private EditBox pos1Edit;
	private EditBox pos2Edit;
	private EditBox listenerEdit;
	private EditBox playbackPosEdit;
	private EditBox radiusEdit;

	public MusicBlockScreen(MusicBlockEntity musicBlock) {
		super(Component.literal("Audiobox"));
		this.musicBlock = musicBlock;
		this.triggerMode = musicBlock.getTriggerMode();
		this.areaGate = musicBlock.isAreaGate();
		this.audioType = musicBlock.getAudioType();
		this.playbackMode = musicBlock.getPlaybackMode();
	}

	@Override
	protected void init() {
		super.init();

		int leftPos = (this.width - BG_WIDTH) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		boolean isChain = this.triggerMode == MusicBlockEntity.TriggerMode.CHAIN;
		boolean gateOn = isChain && this.areaGate;
		boolean isGlobal = this.playbackMode == MusicBlockEntity.PlaybackMode.GLOBAL;

		this.triggerButton = Button.builder(Component.literal("Trigger: " + label(this.triggerMode)), b -> {
			this.triggerMode = this.triggerMode == MusicBlockEntity.TriggerMode.IMPULSE
					? MusicBlockEntity.TriggerMode.CHAIN
					: MusicBlockEntity.TriggerMode.IMPULSE;
			this.rebuildWidgets();
		}).tooltip(Tooltip.create(Component.literal("Impulse: one shot per signal. Chain: conditions checked every tick."))).bounds(leftPos + FIELD_X, topPos + 32, FIELD_WIDTH, 18).build();
		addRenderableWidget(triggerButton);

		this.pos1Edit = new EditBox(this.font, leftPos + FIELD_X, topPos + 52, FIELD_WIDTH, 16, Component.literal("First area corner"));
		this.pos1Edit.setValue(formatPos(musicBlock.getPos1()));
		this.pos1Edit.setEditable(gateOn);
		addRenderableWidget(pos1Edit);

		this.pos2Edit = new EditBox(this.font, leftPos + FIELD_X, topPos + 72, FIELD_WIDTH, 16, Component.literal("Second area corner"));
		this.pos2Edit.setValue(formatPos(musicBlock.getPos2()));
		this.pos2Edit.setEditable(gateOn);
		addRenderableWidget(pos2Edit);

		this.gateButton = Button.builder(Component.literal("Area gate: " + (this.areaGate ? "On" : "Off")), b -> {
			this.areaGate = !this.areaGate;
			this.rebuildWidgets();
		}).tooltip(Tooltip.create(Component.literal("On: only players inside the box hear it. Edit corners or use the area wand."))).bounds(leftPos + FIELD_X, topPos + 92, FIELD_WIDTH, 18).build();
		this.gateButton.visible = isChain;
		addRenderableWidget(gateButton);

		addRenderableWidget(Button.builder(Component.literal("Audio track settings"), b -> {
			applySetupFields();
			this.minecraft.setScreen(new MusicTrackListScreen(this, musicBlock));
		}).tooltip(Tooltip.create(Component.literal("Choose tracks, per-track mix, order, and rules")))
				.bounds(leftPos + 12, topPos + 114, BG_WIDTH - 24, 20).build());

		this.playbackButton = Button.builder(Component.literal("Playback: " + label(this.playbackMode)), b -> {
			this.playbackMode = this.playbackMode == MusicBlockEntity.PlaybackMode.GLOBAL
					? MusicBlockEntity.PlaybackMode.POSITIONAL
					: MusicBlockEntity.PlaybackMode.GLOBAL;
			this.playbackButton.setMessage(Component.literal("Playback: " + label(this.playbackMode)));
			boolean global = this.playbackMode == MusicBlockEntity.PlaybackMode.GLOBAL;
			this.listenerEdit.setVisible(global);
			this.playbackPosEdit.setVisible(!global);
			this.radiusEdit.setVisible(!global);
		}).tooltip(Tooltip.create(Component.literal("Click to switch playback positioning"))).bounds(leftPos + FIELD_X, topPos + 136, FIELD_WIDTH, 18).build();
		addRenderableWidget(playbackButton);

		this.listenerEdit = new EditBox(this.font, leftPos + FIELD_X, topPos + 168, FIELD_WIDTH, 16, Component.literal("Listener selector"));
		this.listenerEdit.setValue(musicBlock.getListenerSelector());
		this.listenerEdit.setVisible(isGlobal);
		addRenderableWidget(listenerEdit);

		this.playbackPosEdit = new EditBox(this.font, leftPos + FIELD_X, topPos + 168, 100, 16, Component.literal("Playback point"));
		this.playbackPosEdit.setValue(formatPos(musicBlock.getPlaybackPos()));
		this.playbackPosEdit.setVisible(!isGlobal);
		addRenderableWidget(playbackPosEdit);

		this.radiusEdit = new EditBox(this.font, leftPos + FIELD_X + 106, topPos + 168, 58, 16, Component.literal("Radius"));
		this.radiusEdit.setValue(String.valueOf(musicBlock.getRadius()));
		this.radiusEdit.setVisible(!isGlobal);
		addRenderableWidget(radiusEdit);

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> saveAndClose())
				.bounds(leftPos + 12, topPos + BG_HEIGHT - 26, 118, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(leftPos + 134, topPos + BG_HEIGHT - 26, 118, 18).build());
	}

	private String label(MusicBlockEntity.TriggerMode mode) {
		return mode == MusicBlockEntity.TriggerMode.CHAIN ? "Chain" : "Impulse";
	}

	private String label(MusicBlockEntity.PlaybackMode mode) {
		return mode == MusicBlockEntity.PlaybackMode.GLOBAL ? "Global" : "Positional";
	}

	private void applySetupFields() {
		musicBlock.setTriggerMode(triggerMode);
		musicBlock.setAreaGate(areaGate);
		musicBlock.setAudioType(audioType);
		if (triggerMode == MusicBlockEntity.TriggerMode.CHAIN && areaGate) {
			musicBlock.setPos1(parsePos(pos1Edit.getValue(), musicBlock.getBlockPos()));
			musicBlock.setPos2(parsePos(pos2Edit.getValue(), musicBlock.getBlockPos()));
		}
		musicBlock.setPlaybackMode(playbackMode);
		if (playbackMode == MusicBlockEntity.PlaybackMode.GLOBAL) {
			musicBlock.setListenerSelector(listenerEdit.getValue());
		} else {
			musicBlock.setPlaybackPos(parsePos(playbackPosEdit.getValue(), musicBlock.getBlockPos()));
			musicBlock.setRadius(parseInt(radiusEdit.getValue(), 16));
		}
	}

	private void saveAndClose() {
		applySetupFields();
		sendUpdate(musicBlock);
		onClose();
	}

	private String formatPos(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private BlockPos parsePos(String text, BlockPos fallback) {
		try {
			String[] parts = text.trim().split("\\s+");
			if (parts.length == 3) {
				return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
			}
		} catch (Exception ignored) {
		}
		return fallback;
	}

	private int parseInt(String text, int fallback) {
		try {
			return Integer.parseInt(text.trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		this.renderBackground(guiGraphics);

		int leftPos = (this.width - BG_WIDTH) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		int labelX = leftPos + 12;
		int labelColor = 0xE0E0E0;
		boolean isChain = this.triggerMode == MusicBlockEntity.TriggerMode.CHAIN;
		boolean isGlobal = this.playbackMode == MusicBlockEntity.PlaybackMode.GLOBAL;

		guiGraphics.fill(leftPos, topPos, leftPos + BG_WIDTH, topPos + BG_HEIGHT, 0xF0101010);
		guiGraphics.renderOutline(leftPos, topPos, BG_WIDTH, BG_HEIGHT, 0xFFA0A0A0);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + 12, 0xFFFFFF);

		guiGraphics.drawString(this.font, "Trigger", labelX, topPos + 37, labelColor, false);
		guiGraphics.drawString(this.font, "Pos1", labelX, topPos + 56, labelColor, false);
		guiGraphics.drawString(this.font, "Pos2", labelX, topPos + 76, labelColor, false);
		if (isChain) {
			guiGraphics.drawString(this.font, "Gate", labelX, topPos + 97, labelColor, false);
		}
		guiGraphics.drawString(this.font, "Playback", labelX, topPos + 141, labelColor, false);

		if (isGlobal) {
			guiGraphics.drawString(this.font, "Listener", labelX, topPos + 173, labelColor, false);
		} else {
			guiGraphics.drawString(this.font, "Point", labelX, topPos + 173, labelColor, false);
			guiGraphics.drawString(this.font, "Radius", leftPos + FIELD_X + 106, topPos + 173, labelColor, false);
		}

		super.render(guiGraphics, mouseX, mouseY, delta);
	}
}
