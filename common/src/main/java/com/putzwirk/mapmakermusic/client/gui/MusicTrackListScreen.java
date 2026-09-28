package com.putzwirk.mapmakermusic.client.gui;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicQueue;
import com.putzwirk.mapmakermusic.library.MusicLibrary;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MusicTrackListScreen extends Screen {

	private static final int BG_HEIGHT = 264;
	private static final int PAD = GuiLayout.SCREEN_PADDING;
	private static final int LIST_TOP = 54;
	private static final int LIST_HEIGHT = 150;
	private static final int ROW_HEIGHT = 32;
	private static final int ROW_GLYPH = GuiIcons.ROW_GLYPH;

	private final Screen parent;
	private final MusicBlockEntity musicBlock;

	private int panelWidth;
	private QueueList queueList;

	public MusicTrackListScreen(Screen parent, MusicBlockEntity musicBlock) {
		super(Component.translatable("mapmakermusic.gui.queues"));
		this.parent = parent;
		this.musicBlock = musicBlock;
	}

	@Override
	protected void init() {
		super.init();

		this.panelWidth = Math.max(320, Math.min(this.width - 40, 400));
		int leftPos = (this.width - panelWidth) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		int content = panelWidth - 2 * PAD;

		addRenderableWidget(Button.builder(Component.literal(GuiIcons.BACK), b -> backToParent())
				.tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.back"))).bounds(leftPos + PAD, topPos + GuiLayout.BACK_TOP, GuiLayout.BACK_SIZE, GuiLayout.BACK_SIZE).build());

		this.queueList = new QueueList(this.minecraft, content, LIST_HEIGHT, topPos + LIST_TOP, topPos + LIST_TOP + LIST_HEIGHT, ROW_HEIGHT);
		this.queueList.setLeftPos(leftPos + PAD);
		refreshQueues();
		addRenderableWidget(queueList);

		addRenderableWidget(Button.builder(Component.translatable("mapmakermusic.gui.add_queue"), b -> {
			musicBlock.addQueue(new MusicQueue());
			this.minecraft.setScreen(new MusicQueueScreen(this, musicBlock, musicBlock.getQueues().size() - 1));
		}).tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.add_queue_tip"))).bounds(leftPos + PAD, topPos + 212, 100, GuiLayout.BUTTON_HEIGHT).build());

		addRenderableWidget(Button.builder(Component.translatable("mapmakermusic.gui.audio_folder"), b -> openMusicFolder())
				.tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.audio_folder_tip")))
				.bounds(leftPos + PAD + 100 + GuiLayout.WIDGET_SPACING, topPos + 212, content - 100 - GuiLayout.WIDGET_SPACING, GuiLayout.BUTTON_HEIGHT).build());

		addRenderableWidget(Button.builder(Component.translatable("mapmakermusic.gui.done"), b -> {
			MusicBlockScreen.sendUpdate(musicBlock);
			this.minecraft.setScreen(parent);
		}).bounds(leftPos + PAD, topPos + BG_HEIGHT - GuiLayout.BOTTOM_OFFSET, content, GuiLayout.BUTTON_HEIGHT).build());
	}

	private void backToParent() {
		MusicBlockScreen.sendUpdate(musicBlock);
		this.minecraft.setScreen(parent);
	}

	private void openMusicFolder() {
		Util.getPlatform().openFile(MusicLibrary.getMusicDir().toFile());
		Minecraft client = Minecraft.getInstance();
		if (client != null) {
			client.keyboardHandler.setClipboard(MusicLibrary.getMusicDir().toString());
		}
	}

	private void openQueue(int index) {
		if (index >= 0 && index < musicBlock.getQueues().size()) {
			GuiIcons.click();
			this.minecraft.setScreen(new MusicQueueScreen(this, musicBlock, index));
		}
	}

	private void removeQueue(int index) {
		if (index >= 0 && index < musicBlock.getQueues().size()) {
			musicBlock.removeQueue(index);
			GuiIcons.click();
			refreshQueues();
		}
	}

	private void moveQueue(int index, int delta) {
		int target = index + delta;
		if (index >= 0 && index < musicBlock.getQueues().size() && target >= 0 && target < musicBlock.getQueues().size()) {
			musicBlock.moveQueue(index, delta);
			GuiIcons.click();
			refreshQueues();
		}
	}

	private void refreshQueues() {
		double scroll = queueList != null ? queueList.getScrollAmount() : 0;
		queueList.clearQueueEntries();
		if (musicBlock.getQueues().isEmpty()) {
			queueList.addQueueEntry(queueList.new Entry(-1));
		}
		for (int i = 0; i < musicBlock.getQueues().size(); i++) {
			queueList.addQueueEntry(queueList.new Entry(i));
		}
		queueList.setScrollAmount(scroll);
	}

	private String clipped(String text, int maxWidth) {
		String shown = this.font.plainSubstrByWidth(text, Math.max(0, maxWidth), false);
		if (shown.length() < text.length()) {
			String ellipsis = "...";
			shown = this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width(ellipsis)), false) + ellipsis;
		}
		return shown;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		this.renderBackground(guiGraphics);
		int leftPos = (this.width - panelWidth) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		guiGraphics.fill(leftPos, topPos, leftPos + panelWidth, topPos + BG_HEIGHT, 0xF0101010);
		guiGraphics.renderOutline(leftPos, topPos, panelWidth, BG_HEIGHT, GuiIcons.boxOutlineColor(musicBlock.getBlockPos(), musicBlock.getOutlineColor()));
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + GuiLayout.TITLE_TOP, 0xFFFFFF);
		guiGraphics.drawString(this.font, clipped(Component.translatable("mapmakermusic.gui.queue_order_hint").getString(), panelWidth - 2 * PAD), leftPos + PAD + 2, topPos + 40, 0x9A9A9A, false);
		super.render(guiGraphics, mouseX, mouseY, delta);
	}

	private class QueueList extends ObjectSelectionList<QueueList.Entry> {
		QueueList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
			super(minecraft, width, height, top, bottom, itemHeight);
			setRenderBackground(false);
			setRenderTopAndBottom(false);
		}

		@Override
		protected void renderBackground(GuiGraphics guiGraphics) {
			guiGraphics.fill(this.x0, this.y0, this.x1, this.y1, 0xFF0A0A0A);
			guiGraphics.renderOutline(this.x0, this.y0, this.x1 - this.x0, this.y1 - this.y0, 0xFF6A6A6A);
		}

		public void addQueueEntry(Entry entry) {
			super.addEntry(entry);
		}

		public void clearQueueEntries() {
			super.clearEntries();
		}

		@Override
		public int getRowWidth() {
			return this.width - 10;
		}

		@Override
		public int getRowLeft() {
			return this.x0 + 2;
		}

		@Override
		protected int getScrollbarPosition() {
			return this.x0 + this.width - 6;
		}

		class Entry extends ObjectSelectionList.Entry<Entry> {
			private final int index;
			private int rowLeft;
			private int rowWidth;
			private int rowTop;

			Entry(int index) {
				this.index = index;
			}

			@Override
			public Component getNarration() {
				return Component.literal(label());
			}

			private String[] lines() {
				if (index < 0) {
					return new String[] {Component.translatable("mapmakermusic.gui.no_queues").getString()};
				}
				MusicQueue queue = musicBlock.getQueues().get(index);
				String tracks = queue.getTrackNames().isEmpty()
						? Component.translatable("mapmakermusic.gui.queue_empty", index + 1).getString()
						: (index + 1) + ". " + String.join(" > ", queue.getTrackNames());
				String channel = Component.translatable("mapmakermusic.gui.channel", Component.translatable(queue.getChannel() == MusicQueue.Channel.MUSIC ? "mapmakermusic.gui.channel.music" : "mapmakermusic.gui.channel.sound").getString()).getString();
				if (queue.isLoop()) {
					channel += Component.translatable("mapmakermusic.gui.looping_suffix").getString();
				}
				String rules = queue.getRuleRoot().describeRules();
				return new String[] {tracks, channel, rules};
			}

			private String label() {
				return String.join(" ", lines());
			}

			@Override
			public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
				this.rowLeft = left;
				this.rowWidth = width;
				this.rowTop = top;
				if (this.index < 0) {
					guiGraphics.drawString(MusicTrackListScreen.this.font, clipped(label(), width - 4), left + 2, top + 7, 0x9A9A9A);
					return;
				}
				boolean empty = musicBlock.getQueues().get(this.index).getTrackNames().isEmpty();
				String[] rows = lines();
				guiGraphics.drawString(MusicTrackListScreen.this.font, clipped(rows[0], width - GuiIcons.SPIN_W - ROW_GLYPH - 8), left + 2, top + 2, empty ? 0xFF8080 : 0xFFFFFF);
				guiGraphics.drawString(MusicTrackListScreen.this.font, clipped(rows[1], width - GuiIcons.SPIN_W - ROW_GLYPH - 8), left + 2, top + 11, 0xB0B0B0, false);
				guiGraphics.drawString(MusicTrackListScreen.this.font, clipped(rows[2], width - GuiIcons.SPIN_W - ROW_GLYPH - 8), left + 2, top + 20, 0xB0B0B0, false);
				int x3 = left + width - ROW_GLYPH;
				int colX = x3 - GuiIcons.SPIN_W - 2;
				int upY = top + 9;
				int downY = top + 16;
				boolean canUp = this.index > 0;
				boolean canDown = this.index < musicBlock.getQueues().size() - 1;
				GuiIcons.drawSpinButton(guiGraphics, MusicTrackListScreen.this.font, colX, upY, GuiIcons.UP, canUp && GuiIcons.inSpin(mouseX, mouseY, colX, upY), canUp, 0);
				GuiIcons.drawSpinButton(guiGraphics, MusicTrackListScreen.this.font, colX, downY, GuiIcons.DOWN, canDown && GuiIcons.inSpin(mouseX, mouseY, colX, downY), canDown, 0);
				GuiIcons.drawRowGlyph(guiGraphics, MusicTrackListScreen.this.font, x3, top + 9, GuiIcons.REMOVE, GuiIcons.inGlyph(mouseX, mouseY, x3, top + 9));
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				if (index < 0) {
					return false;
				}
				int x3 = rowLeft + rowWidth - ROW_GLYPH;
				int colX = x3 - GuiIcons.SPIN_W - 2;
				int upY = rowTop + 9;
				int downY = rowTop + 16;
				if (index > 0 && GuiIcons.inSpin(mouseX, mouseY, colX, upY)) {
					moveQueue(index, -1);
					return true;
				}
				if (index < musicBlock.getQueues().size() - 1 && GuiIcons.inSpin(mouseX, mouseY, colX, downY)) {
					moveQueue(index, 1);
					return true;
				}
				if (GuiIcons.inGlyph(mouseX, mouseY, x3, rowTop + 9)) {
					removeQueue(index);
					return true;
				}
				openQueue(index);
				return true;
			}
		}
	}
}
