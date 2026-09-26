package com.putzwirk.mapmakermusic.client.gui;

import com.putzwirk.mapmakermusic.block.ConditionGroup;
import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import com.putzwirk.mapmakermusic.block.MusicQueue;
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
	private CatalogList catalogList;
	private ActiveList activeList;

	public MusicQueueScreen(Screen parent, MusicBlockEntity block, int queueIndex) {
		super(Component.literal("Queue"));
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
				.tooltip(Tooltip.create(Component.literal("Back"))).bounds(leftPos + PAD, topPos + GuiLayout.BACK_TOP, GuiLayout.BACK_SIZE, GuiLayout.BACK_SIZE).build());

		Button tracksTab = Button.builder(Component.literal("Tracks"), b -> {
			this.conditionsTab = false;
			this.rebuildWidgets();
		}).bounds(leftPos + PAD, topPos + 30, colWidth, GuiLayout.BUTTON_HEIGHT).build();
		tracksTab.active = conditionsTab;
		addRenderableWidget(tracksTab);

		Button conditionsTabButton = Button.builder(Component.literal("Conditions"), b -> {
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
		}).tooltip(Tooltip.create(Component.literal("Music: one track at a time, resumes after relog. Sound: overlapping one-shots.")))
				.bounds(leftPos + PAD, topPos + 166, modeWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(channelButton);

		this.loopButton = Button.builder(loopLabel(), b -> {
			queue.setLoop(!queue.isLoop());
			this.loopButton.setMessage(loopLabel());
		}).tooltip(Tooltip.create(Component.literal("Repeat the queue endlessly")))
				.bounds(leftPos + PAD + modeWidth + GAP, topPos + 166, modeWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(loopButton);

		this.shuffleButton = Button.builder(shuffleLabel(), b -> {
			queue.setShuffle(!queue.isShuffle());
			this.shuffleButton.setMessage(shuffleLabel());
		}).tooltip(Tooltip.create(Component.literal("Shuffle the order each time the playlist starts")))
				.bounds(leftPos + PAD + 2 * (modeWidth + GAP), topPos + 166, modeWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(shuffleButton);

		this.fadeInButton = Button.builder(fadeLabel("Fade in", queue.isFadeIn()), b -> {
			queue.setFadeIn(!queue.isFadeIn());
			this.fadeInButton.setMessage(fadeLabel("Fade in", queue.isFadeIn()));
		}).tooltip(Tooltip.create(Component.literal("Fade in when the queue starts")))
				.bounds(leftPos + PAD, topPos + 188, colWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(fadeInButton);

		this.fadeOutButton = Button.builder(fadeLabel("Fade out", queue.isFadeOut()), b -> {
			queue.setFadeOut(!queue.isFadeOut());
			this.fadeOutButton.setMessage(fadeLabel("Fade out", queue.isFadeOut()));
		}).tooltip(Tooltip.create(Component.literal("Fade out when the queue ends")))
				.bounds(rightX, topPos + 188, colWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(fadeOutButton);

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> backToParent())
				.bounds(leftPos + PAD, topPos + BG_HEIGHT - GuiLayout.BOTTOM_OFFSET, colWidth * 2 + GAP, GuiLayout.BUTTON_HEIGHT).build());

		this.catalogList = null;
		this.activeList = null;
	}

	private void initConditionsTab(int leftPos, int topPos) {
		int listWidth = colWidth * 2 + GAP;
		int catWidth = (listWidth - GAP) * 2 / 5;
		int actWidth = listWidth - GAP - catWidth;

		this.catalogList = new CatalogList(this.minecraft, catWidth, 146, topPos + LIST_TOP, topPos + LIST_TOP + 146, ROW_HEIGHT);
		this.catalogList.setLeftPos(leftPos + PAD);
		this.catalogList.setRenderSelection(false);
		this.catalogList.addCatalogEntry(this.catalogList.new Entry(null));
		for (MusicCondition.Type type : MusicCondition.Type.values()) {
			this.catalogList.addCatalogEntry(this.catalogList.new Entry(type));
		}
		addRenderableWidget(catalogList);

		this.activeList = new ActiveList(this.minecraft, actWidth, 146, topPos + LIST_TOP, topPos + LIST_TOP + 146, ROW_HEIGHT);
		this.activeList.setLeftPos(leftPos + PAD + catWidth + GAP);
		this.activeList.setRenderSelection(false);
		refreshActive();
		addRenderableWidget(activeList);

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> backToParent())
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
		return Component.literal("Channel: " + (music ? GuiIcons.NOTE_MUSIC + "Music" : GuiIcons.NOTE_SOUND + "Sound"));
	}

	private Component loopLabel() {
		return Component.literal("Looping " + bracket(queue().isLoop() ? "YES" : "NO", queue().isLoop()));
	}

	private Component shuffleLabel() {
		return Component.literal("Shuffle " + bracket(queue().isShuffle() ? "YES" : "NO", queue().isShuffle()));
	}

	private static Component fadeLabel(String name, boolean value) {
		return Component.literal(name + " " + bracket(value ? "ON" : "OFF", value));
	}

	private static String bracket(String value, boolean good) {
		return "[" + (good ? "\u00a7a" : "\u00a7c") + value + "\u00a7r]";
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
			String reason = MusicLibrary.playlistBlockReason(track);
			if (reason != null) {
				notifyPlayer(reason);
				return;
			}
		}
		queue().getTracks().add(new MusicQueue.PlaylistItem(stop ? MusicQueue.PlaylistItem.STOP_TRACK : track));
		GuiIcons.click();
		refreshPlaylist();
	}

	private void notifyPlayer(String message) {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.displayClientMessage(Component.literal(message), true);
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

	private ConditionGroup viewedGroup;

	private ConditionGroup viewed() {
		if (viewedGroup == null) {
			viewedGroup = queue().getRuleRoot();
		}
		return viewedGroup;
	}

	private static ConditionGroup findParent(ConditionGroup root, ConditionGroup target) {
		for (Object kid : root.getKids()) {
			if (kid == target) {
				return root;
			}
			if (kid instanceof ConditionGroup group) {
				ConditionGroup found = findParent(group, target);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private void addCondition(MusicCondition.Type type) {
		viewed().getKids().add(type.newDefault());
		GuiIcons.click();
		refreshActive();
	}

	private void addGroup() {
		ConditionGroup group = new ConditionGroup();
		viewed().getKids().add(group);
		viewedGroup = group;
		GuiIcons.click();
		refreshActive();
	}

	private void removeNode(int index) {
		ConditionGroup parent = viewed();
		if (index >= 0 && index < parent.getKids().size()) {
			parent.getKids().remove(index);
			GuiIcons.click();
			refreshActive();
		}
	}

	private void moveNode(int index, int delta) {
		ConditionGroup parent = viewed();
		int target = index + delta;
		if (index >= 0 && index < parent.getKids().size() && target >= 0 && target < parent.getKids().size()) {
			Object node = parent.getKids().remove(index);
			parent.getKids().add(target, node);
			GuiIcons.click();
			refreshActive();
		}
	}

	private void refreshActive() {
		double scroll = activeList != null ? activeList.getScrollAmount() : 0;
		activeList.clearActiveEntries();
		activeList.addActiveEntry(activeList.new Entry(Kind.HEADER));
		if (viewed() != queue().getRuleRoot()) {
			activeList.addActiveEntry(activeList.new Entry(Kind.BACK));
		}
		List<Object> kids = viewed().getKids();
		for (int i = 0; i < kids.size(); i++) {
			activeList.addActiveEntry(activeList.new Entry(Kind.NODE, kids.get(i), i));
		}
		activeList.setScrollAmount(scroll);
	}

	private String breadcrumb() {
		ConditionGroup root = queue().getRuleRoot();
		if (viewed() == root) {
			return "";
		}
		List<String> parts = new ArrayList<>();
		parts.add("Rules");
		appendPath(root, viewed(), parts);
		return String.join(" > ", parts);
	}

	private static boolean appendPath(ConditionGroup current, ConditionGroup target, List<String> parts) {
		for (Object kid : current.getKids()) {
			if (kid == target) {
				parts.add(target.getOp().name());
				return true;
			}
			if (kid instanceof ConditionGroup group && appendPath(group, target, parts)) {
				parts.add(0, group.getOp().name());
				return true;
			}
		}
		return current == target;
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
			guiGraphics.drawString(this.font, "Library", leftPos + PAD + 2, topPos + 52, 0xE0E0E0, false);
			guiGraphics.drawString(this.font, "Playlist", rightX + 2, topPos + 52, 0xE0E0E0, false);
			int divX = leftPos + PAD + colWidth + GAP / 2;
			guiGraphics.fill(divX, topPos + LIST_TOP, divX + 1, topPos + LIST_TOP + LIST_HEIGHT, 0xFF6A6A6A);
		} else {
			guiGraphics.drawString(this.font, breadcrumb(), leftPos + PAD, topPos + 52, 0x9A9A9A, false);
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
					return "Playlist is empty";
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

	private class CatalogList extends RowList<CatalogList.Entry> {
		CatalogList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
			super(minecraft, width, height, top, bottom, itemHeight);
		}

		public void addCatalogEntry(Entry entry) {
			super.addEntry(entry);
		}

		class Entry extends ObjectSelectionList.Entry<Entry> {
			private final MusicCondition.Type type;
			private int rowLeft;
			private int rowWidth;

			Entry(MusicCondition.Type type) {
				this.type = type;
			}

			@Override
			public Component getNarration() {
				return Component.literal(type == null ? "Add group" : type.displayName());
			}

			@Override
			public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
				this.rowLeft = left;
				this.rowWidth = width;
				String name = type == null ? "Group" : type.displayName();
				int bx = left + 2;
				drawRowGlyph(guiGraphics, bx, top + 4, GuiIcons.ADD, inGlyph(mouseX, mouseY, bx, top + 4));
				guiGraphics.drawString(MusicQueueScreen.this.font, clipped(name, width - ROW_GLYPH - 8), left + 2 + ROW_GLYPH + 2, top + 7, type == null ? 0xFFE08A8A : 0xFFFFFF);
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				if (type == null) {
					addGroup();
				} else {
					addCondition(type);
				}
				return true;
			}
		}
	}

	private enum Kind {
		HEADER,
		BACK,
		NODE
	}

	private class ActiveList extends RowList<ActiveList.Entry> {
		ActiveList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
			super(minecraft, width, height, top, bottom, itemHeight);
		}

		public void addActiveEntry(Entry entry) {
			super.addEntry(entry);
		}

		public void clearActiveEntries() {
			super.clearEntries();
		}

		class Entry extends ObjectSelectionList.Entry<Entry> {
			private final Kind kind;
			private final Object node;
			private final int index;
			private int rowLeft;
			private int rowWidth;
			private int rowTop;
			private int opX;
			private int opWidth;

			Entry(Kind kind) {
				this(kind, null, -1);
			}

			Entry(Kind kind, Object node, int index) {
				this.kind = kind;
				this.node = node;
				this.index = index;
			}

			@Override
			public Component getNarration() {
				return Component.literal(label());
			}

			private boolean isGroup() {
				return node instanceof ConditionGroup;
			}

			private ConditionGroup group() {
				return (ConditionGroup) node;
			}

			private String headerText() {
				ConditionGroup current = viewed();
				if (current == queue().getRuleRoot()) {
					return "Match " + current.getOp().name() + " (" + current.ruleCount() + ")";
				}
				return current.getOp().name() + " (" + current.ruleCount() + ")";
			}

			private String label() {
				return switch (kind) {
					case HEADER -> headerText();
					case BACK -> ".. Back";
					case NODE -> isGroup()
							? group().getOp().name() + ": " + groupDescribe() + " >"
							: ((MusicCondition) node).describe();
				};
			}

			private String groupDescribe() {
				String inner = group().describeChildren();
				return inner.isEmpty() ? "(empty)" : inner;
			}

			@Override
			public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
				this.rowLeft = left;
				this.rowWidth = width;
				this.rowTop = top;
				int x4 = left + width - ROW_GLYPH;
				int colX = x4 - GuiIcons.SPIN_W - 2;
				int upY = top + 4;
				int downY = top + 11;
				if (kind == Kind.HEADER) {
					String header = headerText();
					this.opX = left + 2;
					this.opWidth = MusicQueueScreen.this.font.width(header);
					guiGraphics.drawString(MusicQueueScreen.this.font, clipped(header, width - 4), opX, top + 7, 0xFFE08A8A);
					return;
				}
				if (kind == Kind.BACK) {
					this.opWidth = 0;
					guiGraphics.drawString(MusicQueueScreen.this.font, ".. Back", left + 2, top + 7, 0x9A9A9A);
					return;
				}
				this.opWidth = 0;
				int color = isGroup() ? 0xFFE08A8A : 0xFFFFFF;
				guiGraphics.drawString(MusicQueueScreen.this.font, clipped(label(), width - GuiIcons.SPIN_W - ROW_GLYPH - 8), left + 2, top + 7, color);
				boolean canUp = this.index > 0;
				boolean canDown = this.index < viewed().getKids().size() - 1;
				drawSpinButton(guiGraphics, colX, upY, GuiIcons.UP, canUp && inSpin(mouseX, mouseY, colX, upY), canUp, 0);
				drawSpinButton(guiGraphics, colX, downY, GuiIcons.DOWN, canDown && inSpin(mouseX, mouseY, colX, downY), canDown, 0);
				drawRowGlyph(guiGraphics, x4, top + 4, GuiIcons.REMOVE, inGlyph(mouseX, mouseY, x4, top + 4));
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				if (kind == Kind.HEADER) {
					if (mouseX >= opX && mouseX < opX + opWidth) {
						viewed().toggleOp();
						GuiIcons.click();
						refreshActive();
					}
					return true;
				}
				if (kind == Kind.BACK) {
					ConditionGroup parent = findParent(queue().getRuleRoot(), viewed());
					viewedGroup = parent != null ? parent : queue().getRuleRoot();
					refreshActive();
					return true;
				}
				int x4 = rowLeft + rowWidth - ROW_GLYPH;
				int colX = x4 - GuiIcons.SPIN_W - 2;
				int upY = rowTop + 4;
				int downY = rowTop + 11;
				if (index > 0 && GuiIcons.inSpin(mouseX, mouseY, colX, upY)) {
					moveNode(index, -1);
					return true;
				}
				if (index < viewed().getKids().size() - 1 && GuiIcons.inSpin(mouseX, mouseY, colX, downY)) {
					moveNode(index, 1);
					return true;
				}
				if (inGlyph(mouseX, mouseY, x4, rowTop + 4)) {
					removeNode(index);
					return true;
				}
				if (isGroup()) {
					viewedGroup = group();
					refreshActive();
				} else {
					openConditionEditor((MusicCondition) node);
				}
				return true;
			}
		}
	}
}
