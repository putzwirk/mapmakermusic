package com.putzwirk.mapmakermusic.client.gui;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.library.MusicLibrary;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MusicBlockScreen extends Screen {

	private static final int BG_WIDTH = 248;
	private static final int BASE_BG_HEIGHT = 200;
	private static final int AREA_BG_HEIGHT = 198;
	private static final int AREA_EXTRA_HEIGHT = 38;
	private static final int AUDIO_LIST_HEIGHT = 54;
	private static final int LIST_REFRESH_INTERVAL_TICKS = 40;

	private static final List<String> SELECTOR_SUGGESTIONS = List.of("@a", "@p", "@r", "@s", "@e");

	public interface PacketSender {
		void sendUpdatePacket(BlockPos pos, int activationType, int audioType, BlockPos pos1, BlockPos pos2, String audioTrack, int volume, float pitch, boolean loop, boolean persistent, boolean fadeIn, boolean fadeOut, int playbackMode, String listenerSelector, BlockPos playbackPos, int radius);
	}

	private static PacketSender packetSender;

	public static void setPacketSender(PacketSender sender) {
		packetSender = sender;
	}

	private final MusicBlockEntity musicBlock;

	private MusicBlockEntity.ActivationType activationType;
	private MusicBlockEntity.AudioType audioType;
	private MusicBlockEntity.PlaybackMode playbackMode;

	private Button redstoneBtn;
	private Button areaBtn;
	private Button musicBtn;
	private Button soundBtn;
	private Button globalBtn;
	private Button positionalBtn;
	private FolderButton folderBtn;

	private EditBox pos1Edit;
	private EditBox pos2Edit;
	private AudioListWidget audioList;

	private EditBox volumeEdit;
	private EditBox pitchEdit;

	private EditBox listenerEdit;
	private EditBox playbackPosEdit;
	private EditBox radiusEdit;

	private Button saveBtn;
	private Button cancelBtn;

	private String listenerCycleBase = "";
	private int listenerCycleIndex = -1;
	private boolean acceptingListenerCompletion = false;

	private int trackRefreshTicks = 0;

	public MusicBlockScreen(MusicBlockEntity musicBlock) {
		super(Component.literal("Audiobox"));
		this.musicBlock = musicBlock;
		this.activationType = musicBlock.getActivationType();
		this.audioType = musicBlock.getAudioType();
		this.playbackMode = musicBlock.getPlaybackMode();
	}

	@Override
	protected void init() {
		super.init();

		String stashPos1 = this.pos1Edit != null ? this.pos1Edit.getValue() : null;
		String stashPos2 = this.pos2Edit != null ? this.pos2Edit.getValue() : null;
		String stashTrack = this.audioList != null && this.audioList.getSelected() != null ? this.audioList.getSelected().trackName : null;
		String stashVolume = this.volumeEdit != null ? this.volumeEdit.getValue() : null;
		String stashPitch = this.pitchEdit != null ? this.pitchEdit.getValue() : null;
		String stashListener = this.listenerEdit != null ? this.listenerEdit.getValue() : null;
		String stashPlaybackPos = this.playbackPosEdit != null ? this.playbackPosEdit.getValue() : null;
		String stashRadius = this.radiusEdit != null ? this.radiusEdit.getValue() : null;

		int bgWidth = BG_WIDTH;
		int bgHeight = currentBgHeight();
		int leftPos = (this.width - bgWidth) / 2;
		int topPos = (this.height - bgHeight) / 2;

		int contentX = leftPos + 80;
		int labelX = leftPos + 12;
		int shiftInit = this.activationType == MusicBlockEntity.ActivationType.AREA ? AREA_EXTRA_HEIGHT : 0;

		this.redstoneBtn = Button.builder(Component.literal("Redstone"), b -> setActivation(MusicBlockEntity.ActivationType.REDSTONE))
				.bounds(contentX, topPos + 18, 75, 16).build();
		this.areaBtn = Button.builder(Component.literal("Area"), b -> setActivation(MusicBlockEntity.ActivationType.AREA))
				.bounds(contentX + 81, topPos + 18, 75, 16).build();
		addRenderableWidget(redstoneBtn);
		addRenderableWidget(areaBtn);

		this.pos1Edit = new EditBox(this.font, contentX, topPos + 38, 156, 14, Component.literal("Pos1"));
		this.pos1Edit.setValue(stashPos1 != null ? stashPos1 : formatPos(musicBlock.getPos1()));
		addRenderableWidget(pos1Edit);

		this.pos2Edit = new EditBox(this.font, contentX, topPos + 56, 156, 14, Component.literal("Pos2"));
		this.pos2Edit.setValue(stashPos2 != null ? stashPos2 : formatPos(musicBlock.getPos2()));
		addRenderableWidget(pos2Edit);

		this.musicBtn = Button.builder(Component.literal("Music"), b -> setAudioType(MusicBlockEntity.AudioType.MUSIC))
				.bounds(contentX, topPos + 36 + shiftInit, 75, 16).build();
		this.soundBtn = Button.builder(Component.literal("Sound"), b -> setAudioType(MusicBlockEntity.AudioType.SOUND))
				.bounds(contentX + 81, topPos + 36 + shiftInit, 75, 16).build();
		addRenderableWidget(musicBtn);
		addRenderableWidget(soundBtn);

		int listTop = topPos + 56 + shiftInit;
		List<String> tracks = MusicLibrary.scanTrackNames();
		this.audioList = new AudioListWidget(this.minecraft, 156, AUDIO_LIST_HEIGHT, listTop, listTop + AUDIO_LIST_HEIGHT, 12);
		this.audioList.setLeftPos(contentX);
		this.audioList.setRenderBackground(false);
		this.audioList.setRenderTopAndBottom(false);
		String wantTrack = stashTrack != null ? stashTrack : musicBlock.getAudioTrack();
		for (String tr : tracks) {
			AudioEntry entry = new AudioEntry(tr);
			this.audioList.addAudioEntry(entry);
			if (tr.equalsIgnoreCase(wantTrack)) {
				this.audioList.setSelected(entry);
			}
		}
		addRenderableWidget(audioList);

		this.folderBtn = new FolderButton(labelX + 38, listTop);
		addRenderableWidget(folderBtn);

		this.volumeEdit = new EditBox(this.font, contentX, topPos + 114 + shiftInit, 45, 14, Component.literal("Volume"));
		this.volumeEdit.setValue(stashVolume != null ? stashVolume : String.valueOf(musicBlock.getVolume()));
		addRenderableWidget(volumeEdit);

		this.pitchEdit = new EditBox(this.font, contentX + 111, topPos + 114 + shiftInit, 45, 14, Component.literal("Pitch"));
		this.pitchEdit.setValue(stashPitch != null ? stashPitch : String.valueOf(musicBlock.getPitch()));
		addRenderableWidget(pitchEdit);

		this.globalBtn = Button.builder(Component.literal("Global"), b -> setMode(MusicBlockEntity.PlaybackMode.GLOBAL))
				.bounds(contentX, topPos + 132 + shiftInit, 75, 16).build();
		this.positionalBtn = Button.builder(Component.literal("Positional"), b -> setMode(MusicBlockEntity.PlaybackMode.POSITIONAL))
				.bounds(contentX + 81, topPos + 132 + shiftInit, 75, 16).build();
		addRenderableWidget(globalBtn);
		addRenderableWidget(positionalBtn);

		this.listenerEdit = new EditBox(this.font, contentX, topPos + 152 + shiftInit, 156, 14, Component.literal("Listener"));
		this.listenerEdit.setMaxLength(128);
		this.listenerEdit.setValue(stashListener != null ? stashListener : musicBlock.getListenerSelector());
		this.listenerEdit.setResponder(text -> {
			if (!acceptingListenerCompletion) {
				listenerCycleBase = text;
				listenerCycleIndex = -1;
				listenerEdit.setSuggestion(firstListenerRemainder(text));
			}
		});
		addRenderableWidget(listenerEdit);

		this.playbackPosEdit = new EditBox(this.font, contentX, topPos + 152 + shiftInit, 100, 14, Component.literal("Playback Pos"));
		this.playbackPosEdit.setValue(stashPlaybackPos != null ? stashPlaybackPos : formatPos(musicBlock.getPlaybackPos()));
		addRenderableWidget(playbackPosEdit);

		this.radiusEdit = new EditBox(this.font, contentX + 111, topPos + 152 + shiftInit, 45, 14, Component.literal("Radius"));
		this.radiusEdit.setValue(stashRadius != null ? stashRadius : String.valueOf(musicBlock.getRadius()));
		addRenderableWidget(radiusEdit);

		this.saveBtn = Button.builder(Component.literal("Done"), b -> saveAndClose())
				.bounds(leftPos + 29, topPos + bgHeight - 26, 90, 18).build();
		this.cancelBtn = Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(leftPos + 129, topPos + bgHeight - 26, 90, 18).build();
		addRenderableWidget(saveBtn);
		addRenderableWidget(cancelBtn);

		updateVisibility();
		refreshListenerSuggestion();
	}

	private void setActivation(MusicBlockEntity.ActivationType type) {
		if (this.activationType == type) {
			return;
		}
		this.activationType = type;
		refreshLayout();
	}

	private void setAudioType(MusicBlockEntity.AudioType type) {
		if (this.audioType == type) {
			return;
		}
		this.audioType = type;
		refreshLayout();
	}

	private void setMode(MusicBlockEntity.PlaybackMode mode) {
		if (this.playbackMode == mode) {
			return;
		}
		this.playbackMode = mode;
		refreshLayout();
	}

	private void refreshLayout() {
		if (this.minecraft != null) {
			this.minecraft.tell(this::rebuildWidgets);
		} else {
			rebuildWidgets();
		}
	}

	private int currentBgHeight() {
		return this.activationType == MusicBlockEntity.ActivationType.AREA ? AREA_BG_HEIGHT : BASE_BG_HEIGHT;
	}

	private void updateVisibility() {
		int bgHeight = currentBgHeight();
		int topPos = (this.height - bgHeight) / 2;
		boolean isArea = this.activationType == MusicBlockEntity.ActivationType.AREA;

		this.pos1Edit.setVisible(isArea);
		this.pos2Edit.setVisible(isArea);

		int shiftY = isArea ? AREA_EXTRA_HEIGHT : 0;

		this.musicBtn.setY(topPos + 36 + shiftY);
		this.soundBtn.setY(topPos + 36 + shiftY);

		int listTop = topPos + 56 + shiftY;
		this.audioList.setTopPos(listTop);
		this.audioList.setBottomPos(listTop + AUDIO_LIST_HEIGHT);

		this.folderBtn.setY(listTop);

		this.volumeEdit.setY(topPos + 114 + shiftY);
		this.pitchEdit.setY(topPos + 114 + shiftY);

		this.globalBtn.setY(topPos + 132 + shiftY);
		this.positionalBtn.setY(topPos + 132 + shiftY);

		this.listenerEdit.setY(topPos + 152 + shiftY);
		this.playbackPosEdit.setY(topPos + 152 + shiftY);
		this.radiusEdit.setY(topPos + 152 + shiftY);

		this.saveBtn.setY(topPos + bgHeight - 26);
		this.cancelBtn.setY(topPos + bgHeight - 26);

		boolean isGlobal = this.playbackMode == MusicBlockEntity.PlaybackMode.GLOBAL;
		this.globalBtn.visible = !isArea;
		this.positionalBtn.visible = !isArea;
		this.listenerEdit.setVisible(isGlobal && !isArea);
		this.playbackPosEdit.setVisible(!isGlobal && !isArea);
		this.radiusEdit.setVisible(!isGlobal && !isArea);

		this.redstoneBtn.active = (this.activationType != MusicBlockEntity.ActivationType.REDSTONE);
		this.areaBtn.active = (this.activationType != MusicBlockEntity.ActivationType.AREA);
		this.musicBtn.active = (this.audioType != MusicBlockEntity.AudioType.MUSIC);
		this.soundBtn.active = (this.audioType != MusicBlockEntity.AudioType.SOUND);
		this.globalBtn.active = (this.playbackMode != MusicBlockEntity.PlaybackMode.GLOBAL);
		this.positionalBtn.active = (this.playbackMode != MusicBlockEntity.PlaybackMode.POSITIONAL);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 258 && !hasShiftDown() && this.listenerEdit != null && this.listenerEdit.isVisible() && this.listenerEdit.isFocused()) {
			if (acceptListenerCompletion()) {
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void refreshListenerSuggestion() {
		if (this.listenerEdit == null) {
			return;
		}
		this.listenerCycleBase = this.listenerEdit.getValue();
		this.listenerCycleIndex = -1;
		this.listenerEdit.setSuggestion(firstListenerRemainder(this.listenerCycleBase));
	}

	private String firstListenerRemainder(String base) {
		List<String> matches = matchingListeners(listenerCandidates(), base);
		if (matches.isEmpty()) {
			return null;
		}
		return matches.get(0).substring(base.length());
	}

	private boolean acceptListenerCompletion() {
		String current = this.listenerEdit.getValue();
		String base = this.listenerCycleBase != null ? this.listenerCycleBase : current;
		List<String> matches = matchingListeners(listenerCandidates(), base);
		if (matches.isEmpty() && !current.equals(base)) {
			base = current;
			this.listenerCycleBase = base;
			matches = matchingListeners(listenerCandidates(), base);
		}
		if (matches.isEmpty()) {
			return false;
		}
		int next = 0;
		int currentIndex = matches.indexOf(current);
		if (currentIndex >= 0) {
			next = (currentIndex + 1) % matches.size();
		} else if (this.listenerCycleIndex >= 0 && current.equals(base)) {
			next = (this.listenerCycleIndex + 1) % matches.size();
		}
		this.listenerCycleIndex = next;
		this.acceptingListenerCompletion = true;
		try {
			this.listenerEdit.setValue(matches.get(next));
			this.listenerEdit.setSuggestion(null);
		} finally {
			this.acceptingListenerCompletion = false;
		}
		return true;
	}

	private List<String> listenerCandidates() {
		List<String> candidates = new ArrayList<>(SELECTOR_SUGGESTIONS);
		Minecraft client = this.minecraft;
		if (client != null && client.getConnection() != null) {
			List<String> names = new ArrayList<>();
			for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
				String name = info.getProfile().getName();
				if (name != null && !name.isEmpty() && !candidates.contains(name)) {
					names.add(name);
				}
			}
			names.sort(String.CASE_INSENSITIVE_ORDER);
			candidates.addAll(names);
		} else if (client != null && client.level != null) {
			List<String> names = new ArrayList<>();
			for (Player player : client.level.players()) {
				String name = player.getGameProfile().getName();
				if (name != null && !name.isEmpty() && !candidates.contains(name)) {
					names.add(name);
				}
			}
			names.sort(String.CASE_INSENSITIVE_ORDER);
			candidates.addAll(names);
		}
		return candidates;
	}

	private static List<String> matchingListeners(List<String> candidates, String base) {
		List<String> matches = new ArrayList<>();
		if (base == null || base.isEmpty()) {
			return matches;
		}
		String lowerBase = base.toLowerCase(Locale.ROOT);
		for (String candidate : candidates) {
			if (candidate.toLowerCase(Locale.ROOT).startsWith(lowerBase) && !candidate.equalsIgnoreCase(base)) {
				matches.add(candidate);
			}
		}
		return matches;
	}

	private void openMusicFolder() {
		Util.getPlatform().openFile(MusicLibrary.getMusicDir().toFile());
	}

	@Override
	public void tick() {
		super.tick();
		if (++this.trackRefreshTicks >= LIST_REFRESH_INTERVAL_TICKS) {
			this.trackRefreshTicks = 0;
			refreshTrackList();
		}
	}

	private void refreshTrackList() {
		if (this.audioList == null) {
			return;
		}
		List<String> tracks = MusicLibrary.scanTrackNames();
		List<String> current = new ArrayList<>();
		for (AudioEntry entry : this.audioList.children()) {
			current.add(entry.trackName);
		}
		if (current.equals(tracks)) {
			return;
		}
		String selected = this.audioList.getSelected() != null ? this.audioList.getSelected().trackName : null;
		double scroll = this.audioList.getScrollAmount();
		this.audioList.clearAudioEntries();
		for (String track : tracks) {
			AudioEntry entry = new AudioEntry(track);
			this.audioList.addAudioEntry(entry);
			if (track.equalsIgnoreCase(selected)) {
				this.audioList.setSelected(entry);
			}
		}
		this.audioList.setScrollAmount(scroll);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		this.renderBackground(guiGraphics);

		int bgWidth = BG_WIDTH;
		int bgHeight = currentBgHeight();
		int leftPos = (this.width - bgWidth) / 2;
		int topPos = (this.height - bgHeight) / 2;

		guiGraphics.fill(leftPos, topPos, leftPos + bgWidth, topPos + bgHeight, 0xF0101010);
		guiGraphics.renderOutline(leftPos, topPos, bgWidth, bgHeight, 0xFFA0A0A0);

		boolean isArea = this.activationType == MusicBlockEntity.ActivationType.AREA;
		int shiftY = isArea ? AREA_EXTRA_HEIGHT : 0;
		int contentX = leftPos + 80;
		int listTop = topPos + 56 + shiftY;
		int listBottom = listTop + AUDIO_LIST_HEIGHT;
		guiGraphics.fill(contentX, listTop, contentX + 156, listBottom, 0xFF000000);
		guiGraphics.renderOutline(contentX, listTop, 156, listBottom - listTop, 0xFF808080);

		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + 6, 0xFFFFFF);

		int labelX = leftPos + 12;
		int labelColor = 0xE0E0E0;

		guiGraphics.drawString(this.font, "Activation:", labelX, topPos + 22, labelColor, false);
		if (isArea) {
			guiGraphics.drawString(this.font, "Pos1:", labelX, topPos + 42, labelColor, false);
			guiGraphics.drawString(this.font, "Pos2:", labelX, topPos + 60, labelColor, false);
		}

		guiGraphics.drawString(this.font, "Type:", labelX, topPos + 40 + shiftY, labelColor, false);
		guiGraphics.drawString(this.font, "Audio:", labelX, topPos + 60 + shiftY, labelColor, false);
		guiGraphics.drawString(this.font, "Volume:", labelX, topPos + 118 + shiftY, labelColor, false);
		guiGraphics.drawString(this.font, "Pitch:", leftPos + 130, topPos + 118 + shiftY, labelColor, false);

		if (!isArea) {
			guiGraphics.drawString(this.font, "Playback:", labelX, topPos + 136 + shiftY, labelColor, false);

			if (this.playbackMode == MusicBlockEntity.PlaybackMode.GLOBAL) {
				guiGraphics.drawString(this.font, "Listener:", labelX, topPos + 156 + shiftY, labelColor, false);
			} else {
				guiGraphics.drawString(this.font, "Pos / Rad:", labelX, topPos + 156 + shiftY, labelColor, false);
			}
		}

		super.render(guiGraphics, mouseX, mouseY, delta);
	}

	private void saveAndClose() {
		BlockPos p1 = parsePos(pos1Edit.getValue(), musicBlock.getBlockPos());
		BlockPos p2 = parsePos(pos2Edit.getValue(), musicBlock.getBlockPos());
		String selectedTrack = audioList.getSelected() != null ? audioList.getSelected().trackName : "";
		int vol = parseInt(volumeEdit.getValue(), 100);
		float pit = parseFloat(pitchEdit.getValue(), 1.0f);
		boolean isMusic = this.audioType == MusicBlockEntity.AudioType.MUSIC;
		boolean loopVal = isMusic;
		boolean persistentVal = isMusic;
		boolean fadeInVal = isMusic;
		boolean fadeOutVal = isMusic;

		String listenerVal = listenerEdit.getValue();
		BlockPos pbPos = parsePos(playbackPosEdit.getValue(), musicBlock.getBlockPos());
		int rad = parseInt(radiusEdit.getValue(), 16);

		if (packetSender != null) {
			packetSender.sendUpdatePacket(
					musicBlock.getBlockPos(),
					activationType.ordinal(),
					audioType.ordinal(),
					p1, p2,
					selectedTrack,
					vol, pit,
					loopVal, persistentVal,
					fadeInVal, fadeOutVal,
					playbackMode.ordinal(),
					listenerVal,
					pbPos,
					rad
			);
		}

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

	private float parseFloat(String text, float fallback) {
		try {
			return Float.parseFloat(text.trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	private class FolderButton extends Button {
		FolderButton(int x, int y) {
			super(x, y, 18, 18, Component.empty(), button -> openMusicFolder(), Button.DEFAULT_NARRATION);
			setTooltip(Tooltip.create(Component.literal("Open audio folder")));
		}

		@Override
		protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
			super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
			int folderX = getX() + 4;
			int folderY = getY() + 5;
			guiGraphics.fill(folderX, folderY, folderX + 10, folderY + 7, 0xFF4A2E00);
			guiGraphics.fill(folderX + 1, folderY + 1, folderX + 9, folderY + 6, 0xFFF39C00);
			guiGraphics.fill(folderX + 1, folderY, folderX + 5, folderY + 1, 0xFF7A4A00);
			guiGraphics.fill(folderX + 1, folderY + 5, folderX + 9, folderY + 6, 0xFFB26A00);
		}
	}

	private class AudioListWidget extends ObjectSelectionList<AudioEntry> {
		public AudioListWidget(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
			super(minecraft, width, height, top, bottom, itemHeight);
		}

		public void addAudioEntry(AudioEntry entry) {
			this.addEntry(entry);
		}

		public void clearAudioEntries() {
			this.clearEntries();
		}

		public void setTopPos(int top) {
			this.y0 = top;
		}

		public void setBottomPos(int bottom) {
			this.y1 = bottom;
		}

		@Override
		public int getRowWidth() {
			return this.width - 10;
		}

		@Override
		protected int getScrollbarPosition() {
			return this.getRowLeft() + this.getRowWidth();
		}
	}

	private class AudioEntry extends ObjectSelectionList.Entry<AudioEntry> {
		private final String trackName;

		public AudioEntry(String trackName) {
			this.trackName = trackName;
		}

		@Override
		public Component getNarration() {
			return Component.literal(trackName);
		}

		@Override
		public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
			guiGraphics.drawString(MusicBlockScreen.this.font, trackName, left + 2, top + 1, 0xFFFFFF);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			audioList.setSelected(this);
			return true;
		}
	}
}
