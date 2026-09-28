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
	private EditBox priorityEdit;

	public MusicBlockScreen(MusicBlockEntity musicBlock) {
		super(Component.translatable("block.mapmakermusic.music_block"));
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

		this.triggerButton = Button.builder(Component.translatable("mapmakermusic.gui.trigger", label(this.triggerMode)), b -> {
			this.triggerMode = this.triggerMode == MusicBlockEntity.TriggerMode.IMPULSE
					? MusicBlockEntity.TriggerMode.CHAIN
					: MusicBlockEntity.TriggerMode.IMPULSE;
			this.rebuildWidgets();
		}).tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.trigger_tip"))).bounds(leftPos + FIELD_X, topPos + 32, FIELD_WIDTH, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(triggerButton);

		this.pos1Edit = new EditBox(this.font, leftPos + FIELD_X, topPos + 72, FIELD_WIDTH, 16, Component.translatable("mapmakermusic.gui.first_corner"));
		this.pos1Edit.setValue(formatPos(musicBlock.getPos1()));
		this.pos1Edit.setEditable(gateOn);
		addRenderableWidget(pos1Edit);

		this.pos2Edit = new EditBox(this.font, leftPos + FIELD_X, topPos + 92, FIELD_WIDTH, 16, Component.translatable("mapmakermusic.gui.second_corner"));
		this.pos2Edit.setValue(formatPos(musicBlock.getPos2()));
		this.pos2Edit.setEditable(gateOn);
		addRenderableWidget(pos2Edit);

		this.gateButton = Button.builder(Component.translatable("mapmakermusic.gui.area_bounds", Component.translatable(this.areaGate ? "mapmakermusic.gui.on" : "mapmakermusic.gui.off")), b -> {
			this.areaGate = !this.areaGate;
			this.rebuildWidgets();
		}).tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.area_bounds_tip"))).bounds(leftPos + FIELD_X, topPos + 52, FIELD_WIDTH, GuiLayout.BUTTON_HEIGHT).build();
		this.gateButton.visible = isChain;
		addRenderableWidget(gateButton);

		addRenderableWidget(Button.builder(Component.translatable("mapmakermusic.gui.audio_track_settings"), b -> {
			applySetupFields();
			this.minecraft.setScreen(new MusicTrackListScreen(this, musicBlock));
		}).tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.audio_track_settings_tip")))
				.bounds(leftPos + GuiLayout.SCREEN_PADDING, topPos + 114, BG_WIDTH - 2 * GuiLayout.SCREEN_PADDING, GuiLayout.BUTTON_HEIGHT).build());

		this.playbackButton = Button.builder(Component.translatable("mapmakermusic.gui.playback", label(this.playbackMode)), b -> {
			this.playbackMode = this.playbackMode == MusicBlockEntity.PlaybackMode.GLOBAL
					? MusicBlockEntity.PlaybackMode.POSITIONAL
					: MusicBlockEntity.PlaybackMode.GLOBAL;
			this.playbackButton.setMessage(Component.translatable("mapmakermusic.gui.playback", label(this.playbackMode)));
			boolean global = this.playbackMode == MusicBlockEntity.PlaybackMode.GLOBAL;
			boolean impulse = this.triggerMode == MusicBlockEntity.TriggerMode.IMPULSE;
			this.listenerEdit.setVisible(global && impulse);
			this.playbackPosEdit.setVisible(!global);
			this.radiusEdit.setVisible(!global);
		}).tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.playback_tip"))).bounds(leftPos + FIELD_X, topPos + 136, FIELD_WIDTH, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(playbackButton);

		this.listenerEdit = new EditBox(this.font, leftPos + FIELD_X, topPos + 168, FIELD_WIDTH, 16, Component.translatable("mapmakermusic.gui.listener_selector"));
		this.listenerEdit.setValue(musicBlock.getListenerSelector());
		this.listenerEdit.setVisible(isGlobal && this.triggerMode == MusicBlockEntity.TriggerMode.IMPULSE);
		addRenderableWidget(listenerEdit);

		this.playbackPosEdit = new EditBox(this.font, leftPos + 52, topPos + 166, 112, 16, Component.translatable("mapmakermusic.gui.playback_point"));
		this.playbackPosEdit.setValue(formatPos(musicBlock.getPlaybackPos()));
		this.playbackPosEdit.setVisible(!isGlobal);
		addRenderableWidget(playbackPosEdit);

		this.radiusEdit = new EditBox(this.font, leftPos + 212, topPos + 166, 40, 16, Component.translatable("mapmakermusic.gui.radius"));
		this.radiusEdit.setValue(String.valueOf(musicBlock.getRadius()));
		this.radiusEdit.setVisible(!isGlobal);
		addRenderableWidget(radiusEdit);

		this.priorityEdit = new EditBox(this.font, leftPos + FIELD_X, topPos + 186, FIELD_WIDTH, 16, Component.translatable("mapmakermusic.gui.priority"));
		this.priorityEdit.setValue(String.valueOf(musicBlock.getPriority()));
		this.priorityEdit.setFilter(text -> text.matches("-?\\d*"));
		this.priorityEdit.setTooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.priority_tip")));
		addRenderableWidget(priorityEdit);

		addRenderableWidget(Button.builder(Component.translatable("mapmakermusic.gui.done"), b -> saveAndClose())
				.bounds(leftPos + GuiLayout.SCREEN_PADDING, topPos + BG_HEIGHT - GuiLayout.BOTTOM_OFFSET, 116, GuiLayout.BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.translatable("mapmakermusic.gui.cancel"), b -> onClose())
				.bounds(leftPos + GuiLayout.SCREEN_PADDING + 116 + GuiLayout.WIDGET_SPACING, topPos + BG_HEIGHT - GuiLayout.BOTTOM_OFFSET, 116, GuiLayout.BUTTON_HEIGHT).build());
	}

	private static Component label(MusicBlockEntity.TriggerMode mode) {
		return Component.translatable(mode == MusicBlockEntity.TriggerMode.CHAIN ? "mapmakermusic.gui.trigger.chain" : "mapmakermusic.gui.trigger.impulse");
	}

	private static Component label(MusicBlockEntity.PlaybackMode mode) {
		return Component.translatable(mode == MusicBlockEntity.PlaybackMode.GLOBAL ? "mapmakermusic.gui.playback.global" : "mapmakermusic.gui.playback.positional");
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
		if (triggerMode == MusicBlockEntity.TriggerMode.IMPULSE) {
			musicBlock.setListenerSelector(listenerEdit.getValue());
		}
		if (playbackMode != MusicBlockEntity.PlaybackMode.GLOBAL) {
			musicBlock.setPlaybackPos(parsePos(playbackPosEdit.getValue(), musicBlock.getBlockPos()));
			musicBlock.setRadius(parseInt(radiusEdit.getValue(), 16));
		}
		musicBlock.setPriority(parseInt(priorityEdit.getValue(), 0));
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
		int labelX = leftPos + GuiLayout.SCREEN_PADDING;
		int labelColor = 0xE0E0E0;
		boolean isChain = this.triggerMode == MusicBlockEntity.TriggerMode.CHAIN;
		boolean isGlobal = this.playbackMode == MusicBlockEntity.PlaybackMode.GLOBAL;

		guiGraphics.fill(leftPos, topPos, leftPos + BG_WIDTH, topPos + BG_HEIGHT, 0xF0101010);
		guiGraphics.renderOutline(leftPos, topPos, BG_WIDTH, BG_HEIGHT, GuiIcons.boxOutlineColor(musicBlock.getBlockPos(), musicBlock.getOutlineColor()));
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + GuiLayout.TITLE_TOP, 0xFFFFFF);

		guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.trigger_label"), labelX, topPos + 37, labelColor, false);
		boolean gateOn = isChain && this.areaGate;
		if (isChain) {
			guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.bounds"), labelX, topPos + 57, labelColor, false);
		}
		if (gateOn) {
			int swatchX = leftPos + FIELD_X - 20;
			int swatchY = topPos + 54;
			guiGraphics.fill(swatchX, swatchY, swatchX + 14, swatchY + 14, 0xFF000000);
			guiGraphics.fill(swatchX + 1, swatchY + 1, swatchX + 13, swatchY + 13,
					GuiIcons.boxOutlineColor(musicBlock.getBlockPos(), musicBlock.getOutlineColor()));
			guiGraphics.renderOutline(swatchX, swatchY, 14, 14, 0xFFFFFFFF);
		}
		guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.pos1"), labelX, topPos + 76, labelColor, false);
		guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.pos2"), labelX, topPos + 96, labelColor, false);
		guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.playback_label"), labelX, topPos + 141, labelColor, false);

		if (isGlobal) {
			if (this.triggerMode == MusicBlockEntity.TriggerMode.IMPULSE) {
				guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.listener"), labelX, topPos + 173, labelColor, false);
			}
		} else {
			guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.point"), labelX, topPos + 171, labelColor, false);
			guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.radius"), leftPos + 170, topPos + 171, labelColor, false);
		}
		guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.priority"), labelX, topPos + 190, labelColor, false);

		super.render(guiGraphics, mouseX, mouseY, delta);

		if (isChain && this.areaGate) {
			int swatchX = leftPos + FIELD_X - 20;
			int swatchY = topPos + 54;
			if (mouseX >= swatchX && mouseX < swatchX + 14 && mouseY >= swatchY && mouseY < swatchY + 14) {
				guiGraphics.renderTooltip(this.font, Component.translatable("mapmakermusic.gui.outline_color"), mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (delta != 0 && priorityEdit != null && priorityEdit.isMouseOver(mouseX, mouseY)) {
			int value = parseInt(priorityEdit.getValue(), 0) + (delta > 0 ? 1 : -1);
			priorityEdit.setValue(String.valueOf(value));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int leftPos = (this.width - BG_WIDTH) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		if (this.triggerMode == MusicBlockEntity.TriggerMode.CHAIN && this.areaGate) {
			int swatchX = leftPos + FIELD_X - 20;
			int swatchY = topPos + 54;
			if (mouseX >= swatchX && mouseX < swatchX + 14 && mouseY >= swatchY && mouseY < swatchY + 14) {
				applySetupFields();
				this.minecraft.setScreen(new BoxColorScreen(this, musicBlock));
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
}
