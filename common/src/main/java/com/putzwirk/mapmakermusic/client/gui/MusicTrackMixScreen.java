package com.putzwirk.mapmakermusic.client.gui;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MusicTrackMixScreen extends Screen {

	private static final int BG_WIDTH = 220;
	private static final int BG_HEIGHT = 134;
	private static final int PAD = GuiLayout.SCREEN_PADDING;
	private static final int FIELD_WIDTH = BG_WIDTH - 2 * PAD;

	private final Screen parent;
	private final MusicBlockEntity block;
	private final int queueIndex;
	private final int trackIndex;

	private EditBox volumeEdit;
	private EditBox pitchEdit;

	public MusicTrackMixScreen(Screen parent, MusicBlockEntity block, int queueIndex, int trackIndex) {
		super(Component.translatable("mapmakermusic.gui.track_mix"));
		this.parent = parent;
		this.block = block;
		this.queueIndex = queueIndex;
		this.trackIndex = trackIndex;
	}

	private MusicQueue.PlaylistItem item() {
		return block.getQueues().get(queueIndex).getTracks().get(trackIndex);
	}

	@Override
	protected void init() {
		super.init();

		int leftPos = (this.width - BG_WIDTH) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;

		addRenderableWidget(Button.builder(Component.literal(GuiIcons.BACK), b -> backToParent())
				.tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.back"))).bounds(leftPos + PAD, topPos + GuiLayout.BACK_TOP, GuiLayout.BACK_SIZE, GuiLayout.BACK_SIZE).build());

		this.volumeEdit = new EditBox(this.font, leftPos + PAD, topPos + 44, FIELD_WIDTH, GuiLayout.BUTTON_HEIGHT, Component.translatable("mapmakermusic.gui.volume"));
		this.volumeEdit.setHint(Component.translatable("mapmakermusic.gui.volume_hint"));
		this.volumeEdit.setValue(String.valueOf(volumeValue()));
		this.volumeEdit.setResponder(text -> item().setVolume(parseIntOrNull(text)));
		this.volumeEdit.setTooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.scroll_hint")));
		addRenderableWidget(volumeEdit);

		this.pitchEdit = new EditBox(this.font, leftPos + PAD, topPos + 80, FIELD_WIDTH, GuiLayout.BUTTON_HEIGHT, Component.translatable("mapmakermusic.gui.pitch"));
		this.pitchEdit.setHint(Component.translatable("mapmakermusic.gui.pitch_hint"));
		this.pitchEdit.setValue(String.valueOf(pitchValue()));
		this.pitchEdit.setResponder(text -> item().setPitch(parseFloatOrNull(text)));
		this.pitchEdit.setTooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.scroll_hint")));
		addRenderableWidget(pitchEdit);

		addRenderableWidget(Button.builder(Component.translatable("mapmakermusic.gui.done"), b -> backToParent())
				.bounds(leftPos + PAD, topPos + BG_HEIGHT - GuiLayout.BOTTOM_OFFSET, FIELD_WIDTH, GuiLayout.BUTTON_HEIGHT).build());
	}

	private int volumeValue() {
		Integer volume = item().getVolume();
		return volume == null ? block.getVolume() : volume;
	}

	private float pitchValue() {
		Float pitch = item().getPitch();
		return pitch == null ? block.getPitch() : pitch;
	}

	private void backToParent() {
		MusicBlockScreen.sendUpdate(block);
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (delta != 0) {
			if (volumeEdit != null && volumeEdit.isMouseOver(mouseX, mouseY)) {
				int next = Math.max(0, Math.min(200, volumeValue() + (delta > 0 ? 1 : -1)));
				item().setVolume(next);
				volumeEdit.setValue(String.valueOf(next));
				return true;
			}
			if (pitchEdit != null && pitchEdit.isMouseOver(mouseX, mouseY)) {
				float next = Math.max(0.1f, Math.min(4f, pitchValue() + (delta > 0 ? 0.1f : -0.1f)));
				next = Math.round(next * 10f) / 10f;
				item().setPitch(next);
				pitchEdit.setValue(String.valueOf(next));
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	private static Integer parseIntOrNull(String text) {
		try {
			return Math.max(0, Math.min(200, Integer.parseInt(text.trim())));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Float parseFloatOrNull(String text) {
		try {
			return Float.parseFloat(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		this.renderBackground(guiGraphics);
		int leftPos = (this.width - BG_WIDTH) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		guiGraphics.fill(leftPos, topPos, leftPos + BG_WIDTH, topPos + BG_HEIGHT, 0xF0101010);
		guiGraphics.renderOutline(leftPos, topPos, BG_WIDTH, BG_HEIGHT, GuiIcons.boxOutlineColor(block.getBlockPos(), block.getOutlineColor()));
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + GuiLayout.TITLE_TOP, 0xFFFFFF);
		guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.volume"), leftPos + PAD, topPos + 32, 0xE0E0E0, false);
		guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.pitch"), leftPos + PAD, topPos + 68, 0xE0E0E0, false);
		super.render(guiGraphics, mouseX, mouseY, delta);
	}
}
