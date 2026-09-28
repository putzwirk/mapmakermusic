package com.putzwirk.mapmakermusic.client.gui;

import com.putzwirk.mapmakermusic.block.ConditionGroup;
import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import com.putzwirk.mapmakermusic.block.MusicQueue;
import com.putzwirk.mapmakermusic.block.condition.CommandConditionKind;
import com.putzwirk.mapmakermusic.block.condition.ConditionKindRegistry;
import com.putzwirk.mapmakermusic.library.MusicLibrary;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MusicQueueScreen extends Screen {

	private static final int BG_HEIGHT = 240;
	private static final int PAD = GuiLayout.SCREEN_PADDING;
	private static final int GAP = GuiLayout.WIDGET_SPACING;
	private static final int LIST_TOP = 62;
	private static final int LIST_HEIGHT = 100;
	private static final int ROW_HEIGHT = 22;
	private static final int ROW_GLYPH = 14;

	private final Screen parent;
	private final MusicBlockEntity block;
	private final int queueIndex;
	private boolean conditionsTab;

	private int panelWidth;
	private int colWidth;

	private Button channelButton;
	private Button loopButton;
	private Button shuffleButton;
	private Button fadeInButton;
	private Button fadeOutButton;

	private LibraryList libraryList;
	private PlaylistList playlistList;
	private TreeList treeList;

	public MusicQueueScreen(Screen parent, MusicBlockEntity block, int queueIndex) {
		super(Component.translatable("mapmakermusic.gui.queue"));
		this.parent = parent;
		this.block = block;
		this.queueIndex = queueIndex;
	}

	private MusicQueue queue() {
		return block.getQueues().get(Math.max(0, Math.min(queueIndex, block.getQueues().size() - 1)));
	}

	@Override
	protected void init() {
		super.init();

		this.panelWidth = Math.max(320, Math.min(this.width - 40, 400));
		this.colWidth = (this.panelWidth - 2 * PAD - GAP) / 2;
		int leftPos = (this.width - panelWidth) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;

		addRenderableWidget(Button.builder(Component.literal(GuiIcons.BACK), b -> backToParent())
				.tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.back"))).bounds(leftPos + PAD, topPos + GuiLayout.BACK_TOP, GuiLayout.BACK_SIZE, GuiLayout.BACK_SIZE).build());

		Button tracksTab = Button.builder(Component.translatable("mapmakermusic.gui.tracks"), b -> {
			this.conditionsTab = false;
			this.rebuildWidgets();
		}).bounds(leftPos + PAD, topPos + 30, colWidth, GuiLayout.BUTTON_HEIGHT).build();
		tracksTab.active = conditionsTab;
		addRenderableWidget(tracksTab);

		Button conditionsTabButton = Button.builder(Component.translatable("mapmakermusic.gui.conditions"), b -> {
			this.conditionsTab = true;
			this.rebuildWidgets();
		}).bounds(leftPos + PAD + colWidth + GAP, topPos + 30, colWidth, GuiLayout.BUTTON_HEIGHT).build();
		conditionsTabButton.active = !conditionsTab;
		addRenderableWidget(conditionsTabButton);

		if (!conditionsTab) {
			initTracksTab(leftPos, topPos);
		} else {
			initConditionsTab(leftPos, topPos);
		}
	}

	private void backToParent() {
		MusicBlockScreen.sendUpdate(block);
		this.minecraft.setScreen(parent);
	}

	private void initTracksTab(int leftPos, int topPos) {
		MusicQueue queue = queue();
		int rightX = leftPos + PAD + colWidth + GAP;

		this.libraryList = new LibraryList(this.minecraft, colWidth, LIST_HEIGHT, topPos + LIST_TOP, topPos + LIST_TOP + LIST_HEIGHT, ROW_HEIGHT);
		this.libraryList.setLeftPos(leftPos + PAD);
		this.libraryList.setRenderSelection(false);
		this.libraryList.addLibraryEntry(this.libraryList.new Entry(MusicQueue.PlaylistItem.STOP_TRACK));
		for (String track : MusicLibrary.scanTrackNames()) {
			if (!MusicQueue.PlaylistItem.isStop(track)) {
				this.libraryList.addLibraryEntry(this.libraryList.new Entry(track));
			}
		}
		addRenderableWidget(libraryList);

		this.playlistList = new PlaylistList(this.minecraft, colWidth, LIST_HEIGHT, topPos + LIST_TOP, topPos + LIST_TOP + LIST_HEIGHT, ROW_HEIGHT);
		this.playlistList.setLeftPos(rightX);
		refreshPlaylist();
		addRenderableWidget(playlistList);

		int modeWidth = (colWidth * 2 + GAP - 2 * GAP) / 3;
		this.channelButton = Button.builder(channelLabel(), b -> {
			queue.setChannel(queue.getChannel() == MusicQueue.Channel.MUSIC ? MusicQueue.Channel.SOUND : MusicQueue.Channel.MUSIC);
			this.channelButton.setMessage(channelLabel());
		}).tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.channel_tip")))
				.bounds(leftPos + PAD, topPos + 166, modeWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(channelButton);

		this.loopButton = Button.builder(loopLabel(), b -> {
			queue.setLoop(!queue.isLoop());
			this.loopButton.setMessage(loopLabel());
		}).tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.loop_tip")))
				.bounds(leftPos + PAD + modeWidth + GAP, topPos + 166, modeWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(loopButton);

		this.shuffleButton = Button.builder(shuffleLabel(), b -> {
			queue.setShuffle(!queue.isShuffle());
			this.shuffleButton.setMessage(shuffleLabel());
		}).tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.shuffle_tip")))
				.bounds(leftPos + PAD + 2 * (modeWidth + GAP), topPos + 166, modeWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(shuffleButton);

		this.fadeInButton = Button.builder(fadeLabel(Component.translatable("mapmakermusic.gui.fade_in"), queue.isFadeIn()), b -> {
			queue.setFadeIn(!queue.isFadeIn());
			this.fadeInButton.setMessage(fadeLabel(Component.translatable("mapmakermusic.gui.fade_in"), queue.isFadeIn()));
		}).tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.fade_in_tip")))
				.bounds(leftPos + PAD, topPos + 188, colWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(fadeInButton);

		this.fadeOutButton = Button.builder(fadeLabel(Component.translatable("mapmakermusic.gui.fade_out"), queue.isFadeOut()), b -> {
			queue.setFadeOut(!queue.isFadeOut());
			this.fadeOutButton.setMessage(fadeLabel(Component.translatable("mapmakermusic.gui.fade_out"), queue.isFadeOut()));
		}).tooltip(Tooltip.create(Component.translatable("mapmakermusic.gui.fade_out_tip")))
				.bounds(rightX, topPos + 188, colWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(fadeOutButton);

		addRenderableWidget(Button.builder(Component.translatable("mapmakermusic.gui.done"), b -> backToParent())
				.bounds(leftPos + PAD, topPos + BG_HEIGHT - GuiLayout.BOTTOM_OFFSET, colWidth * 2 + GAP, GuiLayout.BUTTON_HEIGHT).build());

		this.treeList = null;
	}

	private void initConditionsTab(int leftPos, int topPos) {
		int listWidth = colWidth * 2 + GAP;

		this.treeList = new TreeList(this.minecraft, listWidth, 118, topPos + LIST_TOP, topPos + LIST_TOP + 118, ROW_HEIGHT);
		this.treeList.setLeftPos(leftPos + PAD);
		this.treeList.setRenderSelection(false);
		refreshTree();
		addRenderableWidget(treeList);

		int buttonsTop = topPos + LIST_TOP + 118 + 4;
		int halfButton = (listWidth - GAP) / 2;
		addRenderableWidget(Button.builder(Component.translatable("mapmakermusic.gui.add_filter"), b -> addCommandCondition(queue().getRuleRoot()))
				.bounds(leftPos + PAD, buttonsTop, halfButton, GuiLayout.BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.translatable("mapmakermusic.gui.add_group"), b -> addInnerGroup(queue().getRuleRoot()))
				.bounds(leftPos + PAD + halfButton + GAP, buttonsTop, halfButton, GuiLayout.BUTTON_HEIGHT).build());

		addRenderableWidget(Button.builder(Component.translatable("mapmakermusic.gui.done"), b -> backToParent())
				.bounds(leftPos + PAD, topPos + BG_HEIGHT - GuiLayout.BOTTOM_OFFSET, listWidth, GuiLayout.BUTTON_HEIGHT).build());

		this.libraryList = null;
		this.playlistList = null;
		this.channelButton = null;
		this.loopButton = null;
		this.shuffleButton = null;
		this.fadeInButton = null;
		this.fadeOutButton = null;
	}

	private void openConditionEditor(MusicCondition condition) {
		GuiIcons.click();
		this.minecraft.setScreen(new MusicConditionScreen(this, block, queueIndex, condition));
	}

	private Component channelLabel() {
		boolean music = queue().getChannel() == MusicQueue.Channel.MUSIC;
		Component name = Component.literal(music ? GuiIcons.NOTE_MUSIC : GuiIcons.NOTE_SOUND)
				.append(Component.translatable(music ? "mapmakermusic.gui.channel.music" : "mapmakermusic.gui.channel.sound"));
		return Component.translatable("mapmakermusic.gui.channel", name);
	}

	private Component loopLabel() {
		return Component.translatable("mapmakermusic.gui.looping", bracket(queue().isLoop() ? "mapmakermusic.gui.yes" : "mapmakermusic.gui.no", queue().isLoop()));
	}

	private Component shuffleLabel() {
		return Component.translatable("mapmakermusic.gui.shuffle", bracket(queue().isShuffle() ? "mapmakermusic.gui.yes" : "mapmakermusic.gui.no", queue().isShuffle()));
	}

	private static Component fadeLabel(Component name, boolean value) {
		return name.copy().append(" ").append(bracket(value ? "ON" : "OFF", value));
	}

	private static Component bracket(String key, boolean good) {
		return Component.translatable(good ? "mapmakermusic.gui.bracket.on" : "mapmakermusic.gui.bracket.off", Component.translatable(key));
	}

	private void openMixEditor(int trackIndex) {
		if (trackIndex < 0 || trackIndex >= queue().getTracks().size()) {
			return;
		}
		if (queue().getTracks().get(trackIndex).isStop()) {
			return;
		}
		GuiIcons.click();
		this.minecraft.setScreen(new MusicTrackMixScreen(this, block, queueIndex, trackIndex));
	}

	private void addTrack(String track) {
		boolean stop = MusicQueue.PlaylistItem.isStop(track);
		for (MusicQueue.PlaylistItem item : queue().getTracks()) {
			if (stop ? item.isStop() : item.getTrack().equalsIgnoreCase(track)) {
				refreshPlaylist();
				return;
			}
		}
		if (!stop) {
			Component reason = MusicLibrary.playlistBlockReason(track);
			if (reason != null) {
				notifyPlayer(reason);
				return;
			}
		}
		queue().getTracks().add(new MusicQueue.PlaylistItem(stop ? MusicQueue.PlaylistItem.STOP_TRACK : track));
		GuiIcons.click();
		refreshPlaylist();
	}

	private void notifyPlayer(Component message) {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.displayClientMessage(message, true);
		}
	}

	private void removeTrack(int index) {
		if (index >= 0 && index < queue().getTracks().size()) {
			queue().getTracks().remove(index);
			GuiIcons.click();
			refreshPlaylist();
		}
	}

	private void moveTrack(int index, int delta) {
		int target = index + delta;
		if (index >= 0 && index < queue().getTracks().size() && target >= 0 && target < queue().getTracks().size()) {
			MusicQueue.PlaylistItem item = queue().getTracks().remove(index);
			queue().getTracks().add(target, item);
			GuiIcons.click();
			refreshPlaylist();
		}
	}

	private void refreshPlaylist() {
		double scroll = playlistList != null ? playlistList.getScrollAmount() : 0;
		playlistList.clearPlaylistEntries();
		if (queue().getTracks().isEmpty()) {
			playlistList.addPlaylistEntry(playlistList.new Entry(-1));
		}
		for (int i = 0; i < queue().getTracks().size(); i++) {
			playlistList.addPlaylistEntry(playlistList.new Entry(i));
		}
		playlistList.setScrollAmount(scroll);
	}

	private record TreeEntry(Object node, ConditionGroup parent, int indexInParent, int depth, List<ConditionGroup.Op> ancestorOps) {
	}

	private static ConditionGroup findParentOf(ConditionGroup root, Object target) {
		for (Object kid : root.getKids()) {
			if (kid == target) {
				return root;
			}
			if (kid instanceof ConditionGroup group) {
				ConditionGroup found = findParentOf(group, target);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private void addCommandCondition(ConditionGroup parent) {
		MusicCondition condition = ConditionKindRegistry.get(CommandConditionKind.ID).newDefault();
		parent.getKids().add(condition);
		GuiIcons.click();
		refreshTree();
		openConditionEditor(condition);
	}

	private void addInnerGroup(ConditionGroup parent) {
		parent.getKids().add(new ConditionGroup());
		GuiIcons.click();
		refreshTree();
	}

	private void removeKid(ConditionGroup parent, int index) {
		if (index >= 0 && index < parent.getKids().size()) {
			parent.getKids().remove(index);
			GuiIcons.click();
			refreshTree();
		}
	}

	private void moveKid(ConditionGroup parent, int index, int delta) {
		int target = index + delta;
		if (index >= 0 && index < parent.getKids().size() && target >= 0 && target < parent.getKids().size()) {
			Object node = parent.getKids().remove(index);
			parent.getKids().add(target, node);
			GuiIcons.click();
			refreshTree();
		}
	}

	private void refreshTree() {
		double scroll = treeList != null ? treeList.getScrollAmount() : 0;
		treeList.clearTreeEntries();
		List<TreeEntry> rows = new ArrayList<>();
		collectTreeEntries(queue().getRuleRoot(), null, 0, List.of(), rows);
		for (TreeEntry row : rows) {
			treeList.addTreeEntry(treeList.new Entry(row));
		}
		treeList.setScrollAmount(scroll);
	}

	private static void collectTreeEntries(ConditionGroup group, ConditionGroup parent, int depth, List<ConditionGroup.Op> ancestorOps, List<TreeEntry> rows) {
		int index = parent == null ? -1 : parent.getKids().indexOf(group);
		rows.add(new TreeEntry(group, parent, index, depth, List.copyOf(ancestorOps)));
		List<ConditionGroup.Op> childOps = new ArrayList<>(ancestorOps);
		childOps.add(group.getOp());
		List<Object> kids = group.getKids();
		for (int i = 0; i < kids.size(); i++) {
			Object kid = kids.get(i);
			if (kid instanceof ConditionGroup sub) {
				collectTreeEntries(sub, group, depth + 1, childOps, rows);
			} else {
				rows.add(new TreeEntry(kid, group, i, depth + 1, List.copyOf(childOps)));
			}
		}
	}

	private static int opColor(ConditionGroup.Op op) {
		return op == ConditionGroup.Op.ALL ? 0xFF6B9EFF : 0xFFFFB35C;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		this.renderBackground(guiGraphics);
		int leftPos = (this.width - panelWidth) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		guiGraphics.fill(leftPos, topPos, leftPos + panelWidth, topPos + BG_HEIGHT, 0xF0101010);
		guiGraphics.renderOutline(leftPos, topPos, panelWidth, BG_HEIGHT, GuiIcons.boxOutlineColor(block.getBlockPos(), block.getOutlineColor()));
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + GuiLayout.TITLE_TOP, 0xFFFFFF);

		if (!conditionsTab) {
			int rightX = leftPos + PAD + colWidth + GAP;
			guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.library"), leftPos + PAD + 2, topPos + 52, 0xE0E0E0, false);
			guiGraphics.drawString(this.font, Component.translatable("mapmakermusic.gui.playlist"), rightX + 2, topPos + 52, 0xE0E0E0, false);
			int divX = leftPos + PAD + colWidth + GAP / 2;
			guiGraphics.fill(divX, topPos + LIST_TOP, divX + 1, topPos + LIST_TOP + LIST_HEIGHT, 0xFF6A6A6A);
		} else {
			guiGraphics.drawString(this.font, queue().getRuleRoot().ruleCount(), leftPos + PAD, topPos + 52, 0x9A9A9A, false);
		}

		super.render(guiGraphics, mouseX, mouseY, delta);
	}

	private void drawRowGlyph(GuiGraphics guiGraphics, int x, int y, String symbol, boolean hovered) {
		GuiIcons.drawRowGlyph(guiGraphics, this.font, x, y, symbol, hovered);
	}

	private boolean inGlyph(double mouseX, double mouseY, int x, int y) {
		return GuiIcons.inGlyph(mouseX, mouseY, x, y);
	}

	private void drawSpinButton(GuiGraphics guiGraphics, int x, int y, String symbol, boolean hovered, boolean enabled, int textDy) {
		GuiIcons.drawSpinButton(guiGraphics, this.font, x, y, symbol, hovered, enabled, textDy);
	}

	private boolean inSpin(double mouseX, double mouseY, int x, int y) {
		return GuiIcons.inSpin(mouseX, mouseY, x, y);
	}

	private String clipped(String text, int maxWidth) {
		String shown = this.font.plainSubstrByWidth(text, Math.max(0, maxWidth), false);
		if (shown.length() < text.length()) {
			String ellipsis = "...";
			shown = this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width(ellipsis)), false) + ellipsis;
		}
		return shown;
	}

	private abstract class RowList<T extends ObjectSelectionList.Entry<T>> extends ObjectSelectionList<T> {
		RowList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
			super(minecraft, width, height, top, bottom, itemHeight);
			setRenderBackground(false);
			setRenderTopAndBottom(false);
		}

		@Override
		protected void renderBackground(GuiGraphics guiGraphics) {
			guiGraphics.fill(this.x0, this.y0, this.x1, this.y1, 0xFF0A0A0A);
			guiGraphics.renderOutline(this.x0, this.y0, this.x1 - this.x0, this.y1 - this.y0, 0xFF6A6A6A);
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
	}

	private class LibraryList extends RowList<LibraryList.Entry> {
		LibraryList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
			super(minecraft, width, height, top, bottom, itemHeight);
		}

		public void addLibraryEntry(Entry entry) {
			super.addEntry(entry);
		}

		class Entry extends ObjectSelectionList.Entry<Entry> {
			private final String track;
			private int rowLeft;
			private int rowWidth;

			Entry(String track) {
				this.track = track;
			}

			@Override
			public Component getNarration() {
				return Component.literal(track);
			}

			@Override
			public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
				this.rowLeft = left;
				this.rowWidth = width;
				String duration = "";
				if (!MusicQueue.PlaylistItem.isStop(track)) {
					MusicLibrary.TrackInfo info = MusicLibrary.trackInfo().get(track);
					duration = MusicLibrary.formatDuration(info == null ? -1f : info.durationSeconds());
				}
				int durationWidth = duration.isEmpty() ? 0 : MusicQueueScreen.this.font.width(duration) + 4;
				int bx = left + 2;
				drawRowGlyph(guiGraphics, bx, top + 4, GuiIcons.ADD, inGlyph(mouseX, mouseY, bx, top + 4));
				guiGraphics.drawString(MusicQueueScreen.this.font, clipped(track, width - ROW_GLYPH - 8 - durationWidth), left + 2 + ROW_GLYPH + 2, top + 7,
						MusicQueue.PlaylistItem.isStop(track) ? 0xFFE07A7A : 0xFFFFFF);
				if (!duration.isEmpty()) {
					guiGraphics.drawString(MusicQueueScreen.this.font, duration, left + width - 2 - durationWidth + 2, top + 7, 0x9A9A9A, false);
				}
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				addTrack(track);
				return true;
			}
		}
	}

	private class PlaylistList extends RowList<PlaylistList.Entry> {
		PlaylistList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
			super(minecraft, width, height, top, bottom, itemHeight);
		}

		public void addPlaylistEntry(Entry entry) {
			super.addEntry(entry);
		}

		public void clearPlaylistEntries() {
			super.clearEntries();
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

			private String label() {
				if (index < 0) {
					return Component.translatable("mapmakermusic.gui.playlist_empty").getString();
				}
				MusicQueue.PlaylistItem item = queue().getTracks().get(index);
				return (index + 1) + ". " + item.getTrack();
			}

			@Override
			public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
				this.rowLeft = left;
				this.rowWidth = width;
				this.rowTop = top;
				if (this.index < 0) {
					guiGraphics.drawString(MusicQueueScreen.this.font, clipped(label(), width - 4), left + 2, top + 7, 0x9A9A9A);
					return;
				}
				int textColor = queue().getTracks().get(this.index).isStop() ? 0xFFE07A7A : 0xFFFFFF;
				guiGraphics.drawString(MusicQueueScreen.this.font, clipped(label(), width - GuiIcons.SPIN_W - ROW_GLYPH - 8), left + 2, top + 7, textColor);
				int x4 = left + width - ROW_GLYPH;
				int colX = x4 - GuiIcons.SPIN_W - 2;
				int upY = top + 4;
				int downY = top + 11;
				boolean canUp = this.index > 0;
				boolean canDown = this.index < queue().getTracks().size() - 1;
				drawSpinButton(guiGraphics, colX, upY, GuiIcons.UP, canUp && inSpin(mouseX, mouseY, colX, upY), canUp, 0);
				drawSpinButton(guiGraphics, colX, downY, GuiIcons.DOWN, canDown && inSpin(mouseX, mouseY, colX, downY), canDown, 0);
				drawRowGlyph(guiGraphics, x4, top + 4, GuiIcons.REMOVE, inGlyph(mouseX, mouseY, x4, top + 4));
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				if (index < 0) {
					return false;
				}
				int x4 = rowLeft + rowWidth - ROW_GLYPH;
				int colX = x4 - GuiIcons.SPIN_W - 2;
				int upY = rowTop + 4;
				int downY = rowTop + 11;
				if (index > 0 && GuiIcons.inSpin(mouseX, mouseY, colX, upY)) {
					moveTrack(index, -1);
					return true;
				}
				if (index < queue().getTracks().size() - 1 && GuiIcons.inSpin(mouseX, mouseY, colX, downY)) {
					moveTrack(index, 1);
					return true;
				}
				if (inGlyph(mouseX, mouseY, x4, rowTop + 4)) {
					removeTrack(index);
					return true;
				}
				openMixEditor(index);
				return true;
			}
		}
	}

	private class TreeList extends RowList<TreeList.Entry> {
		TreeList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
			super(minecraft, width, height, top, bottom, itemHeight);
		}

		public void addTreeEntry(Entry entry) {
			super.addEntry(entry);
		}

		public void clearTreeEntries() {
			super.clearEntries();
		}

		class Entry extends ObjectSelectionList.Entry<Entry> {
			private static final int INDENT = 12;
			private static final int PILL_HEIGHT = 14;
			private final TreeEntry row;
			private int rowLeft;
			private int rowWidth;
			private int rowTop;
			private int pillX;
			private int pillWidth;
			private int filterX;
			private int filterWidth;
			private int innerX;
			private int innerWidth;

			Entry(TreeEntry row) {
				this.row = row;
			}

			@Override
			public Component getNarration() {
				if (row.node() instanceof ConditionGroup group) {
					return Component.translatable("mapmakermusic.gui.group_narration", Component.translatable(group.getOp() == ConditionGroup.Op.ALL ? "mapmakermusic.gui.op.all" : "mapmakermusic.gui.op.any"));
				}
				return Component.literal(((MusicCondition) row.node()).describe());
			}

			private boolean isGroup() {
				return row.node() instanceof ConditionGroup;
			}

			private ConditionGroup group() {
				return (ConditionGroup) row.node();
			}

			private int contentX(int left) {
				return left + 4 + row.depth() * INDENT;
			}

			private void drawGuides(GuiGraphics guiGraphics, int left, int top, int height) {
				for (int i = 0; i < row.depth(); i++) {
					int gx = left + 4 + i * INDENT;
					guiGraphics.fill(gx, top + 1, gx + 1, top + height - 1, opColor(row.ancestorOps().get(i)));
				}
			}

			private boolean inBand(double mouseX, double mouseY, int x, int w) {
				return mouseX >= x && mouseX < x + w && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
			}

			private int removeX(int left, int width) {
				return left + width - ROW_GLYPH - 8;
			}

			@Override
			public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
				this.rowLeft = left;
				this.rowWidth = width;
				this.rowTop = top;
				this.pillWidth = 0;
				this.filterWidth = 0;
				this.innerWidth = 0;
				drawGuides(guiGraphics, left, top, height);
				int cx = contentX(left);
				if (isGroup()) {
					ConditionGroup group = group();
					int color = opColor(group.getOp());
					if (row.depth() > 0) {
						int sx = left + 4 + (row.depth() - 1) * INDENT;
						guiGraphics.fill(sx, top + height / 2, cx + 2, top + height / 2 + 1, color);
					}
					String opName = Component.translatable(group.getOp() == ConditionGroup.Op.ALL ? "mapmakermusic.gui.op.all" : "mapmakermusic.gui.op.any").getString();
					this.pillX = cx + 2;
					this.pillWidth = MusicQueueScreen.this.font.width(opName) + 10;
					guiGraphics.fill(pillX, top + 4, pillX + pillWidth, top + 4 + PILL_HEIGHT, 0xFF141414);
					guiGraphics.renderOutline(pillX, top + 4, pillWidth, PILL_HEIGHT, color);
					guiGraphics.drawCenteredString(MusicQueueScreen.this.font, opName, pillX + pillWidth / 2, top + 7, color);
					int nx = pillX + pillWidth + 4;
					String count = "(" + group.ruleCount() + ")";
					guiGraphics.drawString(MusicQueueScreen.this.font, count, nx, top + 7, 0x9A9A9A, false);
					nx += MusicQueueScreen.this.font.width(count) + 8;
					this.filterX = nx;
					String filterText = Component.translatable("mapmakermusic.gui.add_filter").getString();
					this.filterWidth = MusicQueueScreen.this.font.width(filterText);
					guiGraphics.drawString(MusicQueueScreen.this.font, filterText, filterX, top + 7,
							inBand(mouseX, mouseY, filterX, filterWidth) ? 0xFFFFFF : 0xFF7FB2FF, false);
					nx += filterWidth + 8;
					this.innerX = nx;
					String innerText = Component.translatable("mapmakermusic.gui.add_group").getString();
					this.innerWidth = MusicQueueScreen.this.font.width(innerText);
					guiGraphics.drawString(MusicQueueScreen.this.font, innerText, innerX, top + 7,
							inBand(mouseX, mouseY, innerX, innerWidth) ? 0xFFFFFF : 0xFF7FB2FF, false);
					if (row.parent() != null) {
						int x4 = removeX(left, width);
						drawRowGlyph(guiGraphics, x4, top + 4, GuiIcons.REMOVE, inBand(mouseX, mouseY, x4, ROW_GLYPH));
					}
					return;
				}
				int stubColor = row.ancestorOps().isEmpty() ? 0xFF6A6A6A : opColor(row.ancestorOps().get(row.ancestorOps().size() - 1));
				if (row.depth() > 0) {
					int sx = left + 4 + (row.depth() - 1) * INDENT;
					guiGraphics.fill(sx, top + height / 2, cx + 2, top + height / 2 + 1, stubColor);
				}
				guiGraphics.drawString(MusicQueueScreen.this.font,
						clipped(((MusicCondition) row.node()).describe(), width - (cx + 2 - left) - GuiIcons.SPIN_W - ROW_GLYPH - 16),
						cx + 2, top + 7, 0xFFFFFF);
				int x4 = removeX(left, width);
				int colX = x4 - GuiIcons.SPIN_W - 2;
				int upY = top + 4;
				int downY = top + 11;
				int size = row.parent().getKids().size();
				boolean canUp = row.indexInParent() > 0;
				boolean canDown = row.indexInParent() < size - 1;
				drawSpinButton(guiGraphics, colX, upY, GuiIcons.UP, canUp && inSpin(mouseX, mouseY, colX, upY), canUp, 0);
				drawSpinButton(guiGraphics, colX, downY, GuiIcons.DOWN, canDown && inSpin(mouseX, mouseY, colX, downY), canDown, 0);
				drawRowGlyph(guiGraphics, x4, top + 4, GuiIcons.REMOVE, inGlyph(mouseX, mouseY, x4, top + 4));
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				if (isGroup()) {
					ConditionGroup group = group();
					if (pillWidth > 0 && inBand(mouseX, mouseY, pillX, pillWidth)) {
						group.toggleOp();
						GuiIcons.click();
						refreshTree();
						return true;
					}
					if (filterWidth > 0 && inBand(mouseX, mouseY, filterX, filterWidth)) {
						addCommandCondition(group);
						return true;
					}
					if (innerWidth > 0 && inBand(mouseX, mouseY, innerX, innerWidth)) {
						addInnerGroup(group);
						return true;
					}
					if (row.parent() != null) {
						int x4 = removeX(rowLeft, rowWidth);
						if (inBand(mouseX, mouseY, x4, ROW_GLYPH)) {
							removeKid(row.parent(), row.indexInParent());
							return true;
						}
					}
					return true;
				}
				int x4 = removeX(rowLeft, rowWidth);
				int colX = x4 - GuiIcons.SPIN_W - 2;
				int size = row.parent().getKids().size();
				if (row.indexInParent() > 0 && mouseX >= colX && mouseX < colX + GuiIcons.SPIN_W
						&& mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT / 2) {
					moveKid(row.parent(), row.indexInParent(), -1);
					return true;
				}
				if (row.indexInParent() < size - 1 && mouseX >= colX && mouseX < colX + GuiIcons.SPIN_W
						&& mouseY >= rowTop + ROW_HEIGHT / 2 && mouseY < rowTop + ROW_HEIGHT) {
					moveKid(row.parent(), row.indexInParent(), 1);
					return true;
				}
				if (inBand(mouseX, mouseY, x4, ROW_GLYPH)) {
					removeKid(row.parent(), row.indexInParent());
					return true;
				}
				openConditionEditor((MusicCondition) row.node());
				return true;
			}
		}
	}
}
