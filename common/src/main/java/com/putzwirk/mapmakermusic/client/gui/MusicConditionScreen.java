package com.putzwirk.mapmakermusic.client.gui;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import com.putzwirk.mapmakermusic.block.condition.CommandConditionKind;
import com.putzwirk.mapmakermusic.block.condition.ConditionKind;
import com.putzwirk.mapmakermusic.block.condition.FieldSpec;
import com.putzwirk.mapmakermusic.network.ConditionTestNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class MusicConditionScreen extends Screen {

	private static final int MAX_SUGGESTIONS = 8;
	private static final int SUGGESTION_ROW = 11;
	private static final int NOTE_ROW = 14;

	private final Screen parent;
	private final MusicBlockEntity block;
	private final int queueIndex;
	private final MusicCondition original;
	private final MusicCondition condition;
	private final int bgWidth;
	private final int bgHeight;

	private EditBox suggestionBox;
	private String suggestParamKey;
	private String suggestKey;
	private final List<EditBox> doubleBoxes = new ArrayList<>();
	private final List<String> doubleKeys = new ArrayList<>();
	private final Map<String, Button> actionButtons = new HashMap<>();
	private List<String> suggestions = List.of();
	private int suggestionIndex;
	private List<String> serverSuggestions = List.of();
	private int suggestStart = -1;
	private String lastSuggestRequest;
	private String testStatus;
	private int testStatusColor;
	private int testStatusY = -1;
	private List<String> notes = List.of();
	private int notesY;

	public MusicConditionScreen(Screen parent, MusicBlockEntity block, int queueIndex, MusicCondition condition) {
		super(Component.literal(titleOf(condition)));
		this.parent = parent;
		this.block = block;
		this.queueIndex = queueIndex;
		this.original = condition;
		this.condition = condition.copy();
		ConditionKind kind = condition.kind();
		this.bgWidth = kind == null ? 248 : kind.editorWidth();
		this.bgHeight = kind == null ? 150 : kind.editorHeight();
	}

	private static String titleOf(MusicCondition condition) {
		ConditionKind kind = condition.kind();
		return kind == null ? "Unknown" : kind.displayName();
	}

	@Override
	protected void init() {
		super.init();
		doubleBoxes.clear();
		doubleKeys.clear();
		actionButtons.clear();
		suggestionBox = null;
		suggestParamKey = null;
		suggestKey = null;

		int leftPos = (this.width - bgWidth) / 2;
		int topPos = (this.height - bgHeight) / 2;
		int x = leftPos + GuiLayout.SCREEN_PADDING;
		int contentWidth = bgWidth - 2 * GuiLayout.SCREEN_PADDING;
		int fieldWidth = (contentWidth - GuiLayout.WIDGET_SPACING) / 2;

		addRenderableWidget(Button.builder(Component.literal(GuiIcons.BACK), b -> {
			MusicBlockScreen.sendUpdate(block);
			this.minecraft.setScreen(parent);
		}).tooltip(Tooltip.create(Component.literal("Back"))).bounds(x, topPos + GuiLayout.BACK_TOP, GuiLayout.BACK_SIZE, GuiLayout.BACK_SIZE).build());

		ConditionKind kind = condition.kind();
		int row = 0;
		boolean hasAction = false;
		if (kind != null) {
			for (FieldSpec spec : kind.editorFields()) {
				if (!kind.fieldVisible(spec, condition)) {
					continue;
				}
				if (spec.type() == FieldSpec.FieldType.ACTION) {
					hasAction = true;
				}
				row += addField(kind, spec, x, topPos + 30 + row * GuiLayout.SECTION_SPACING, contentWidth, fieldWidth);
			}
		}
		int below = topPos + 30 + row * GuiLayout.SECTION_SPACING;
		if (hasAction) {
			testStatusY = below;
			below += GuiLayout.SECTION_SPACING;
		}
		notes = kind == null ? List.of() : kind.editorNotes();
		notesY = below;

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> saveAndClose())
				.bounds(x, topPos + bgHeight - GuiLayout.BOTTOM_OFFSET, contentWidth, GuiLayout.BUTTON_HEIGHT).build());
	}

	private int addField(ConditionKind kind, FieldSpec spec, int x, int y, int contentWidth, int fieldWidth) {
		return switch (spec.type()) {
			case MODE_CYCLE -> {
				addModeButton(kind, x, y, contentWidth);
				yield 1;
			}
			case TEXT, ENTITY_ID, LONG_TEXT -> {
				addTextBox(spec, x, y, contentWidth);
				yield 1;
			}
			case NUMBER -> {
				addNumberBox(spec, x, y, contentWidth);
				yield 1;
			}
			case RANGE -> {
				addRangeBoxes(spec, x, y, fieldWidth);
				yield 1;
			}
			case ACTION -> {
				addActionButton(kind, spec, x, y, contentWidth);
				yield 1;
			}
			default -> {
				yield 0;
			}
		};
	}

	private void addModeButton(ConditionKind kind, int x, int y, int contentWidth) {
		addRenderableWidget(Button.builder(Component.literal("Mode: " + title(kind.modeOf(condition))), b -> {
			kind.cycleMode(condition);
			this.rebuildWidgets();
		}).bounds(x, y, contentWidth, GuiLayout.BUTTON_HEIGHT).build());
	}

	private void addTextBox(FieldSpec spec, int x, int y, int contentWidth) {
		EditBox box = new EditBox(this.font, x, y, contentWidth, GuiLayout.BUTTON_HEIGHT, Component.literal(spec.label()));
		box.setHint(Component.literal(spec.hint() == null ? "value" : spec.hint()));
		box.setMaxLength(spec.type() == FieldSpec.FieldType.LONG_TEXT ? 512 : 128);
		box.setValue(condition.params().getString(spec.key()));
		box.setResponder(text -> {
			condition.params().putString(spec.key(), text);
			if (box == suggestionBox) {
				refreshSuggestions();
			}
		});
		addRenderableWidget(box);
		if (spec.suggest() != null && suggestionBox == null
				&& (spec.type() == FieldSpec.FieldType.TEXT || spec.type() == FieldSpec.FieldType.ENTITY_ID || spec.type() == FieldSpec.FieldType.LONG_TEXT)) {
			suggestionBox = box;
			suggestParamKey = spec.key();
			suggestKey = spec.suggest();
		}
	}

	private void addNumberBox(FieldSpec spec, int x, int y, int contentWidth) {
		EditBox box = new EditBox(this.font, x, y, contentWidth, GuiLayout.BUTTON_HEIGHT, Component.literal(spec.label()));
		box.setHint(Component.literal(spec.label()));
		box.setValue(format(dbl(spec.key())));
		box.setResponder(text -> condition.params().putDouble(spec.key(), parse(text, dbl(spec.key()))));
		box.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
		addRenderableWidget(box);
		doubleBoxes.add(box);
		doubleKeys.add(spec.key());
	}

	private void addRangeBoxes(FieldSpec spec, int x, int y, int fieldWidth) {
		EditBox minBox = new EditBox(this.font, x, y, fieldWidth, GuiLayout.BUTTON_HEIGHT, Component.literal(spec.label()));
		minBox.setValue(format(dbl(spec.key())));
		minBox.setResponder(text -> condition.params().putDouble(spec.key(), parse(text, dbl(spec.key()))));
		minBox.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
		addRenderableWidget(minBox);
		doubleBoxes.add(minBox);
		doubleKeys.add(spec.key());

		EditBox maxBox = new EditBox(this.font, x + fieldWidth + GuiLayout.WIDGET_SPACING, y, fieldWidth, GuiLayout.BUTTON_HEIGHT, Component.literal(spec.secondLabel()));
		maxBox.setValue(format(dbl(spec.secondKey())));
		maxBox.setResponder(text -> condition.params().putDouble(spec.secondKey(), parse(text, dbl(spec.secondKey()))));
		maxBox.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
		addRenderableWidget(maxBox);
		doubleBoxes.add(maxBox);
		doubleKeys.add(spec.secondKey());
	}

	private void addActionButton(ConditionKind kind, FieldSpec spec, int x, int y, int contentWidth) {
		Button button = Button.builder(Component.literal(spec.label()), b -> {
			String result = kind.runAction(spec.key(), condition, this.minecraft);
			b.setMessage(Component.literal(result == null ? spec.label() : spec.label() + ": " + result));
		}).bounds(x, y, contentWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(button);
		actionButtons.put(spec.key(), button);
	}

	public static void handleTestResult(String command, boolean pass) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || !(client.screen instanceof MusicConditionScreen screen)) {
			return;
		}
		screen.showTestResult(command, pass);
	}

	private void showTestResult(String command, boolean pass) {
		Button button = actionButtons.get(CommandConditionKind.TEST_ACTION);
		if (button == null) {
			return;
		}
		String current = condition.params().getString(CommandConditionKind.COMMAND_KEY).trim();
		if (!current.equals(command)) {
			return;
		}
		testStatus = pass ? "PASS" : "FAIL";
		testStatusColor = pass ? 0x55FF55 : 0xFF5555;
	}

	public static void handleSuggestionResult(String echo, int start, List<String> texts) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || !(client.screen instanceof MusicConditionScreen screen)) {
			return;
		}
		screen.showSuggestions(echo, start, texts);
	}

	private void showSuggestions(String echo, int start, List<String> texts) {
		if (suggestionBox == null || !"command".equals(suggestKey)) {
			return;
		}
		if (!suggestionBox.getValue().equals(echo)) {
			return;
		}
		serverSuggestions = List.copyOf(texts);
		suggestStart = start;
		refreshSuggestions();
	}

	private double dbl(String key) {
		return condition.params().contains(key) ? condition.params().getDouble(key) : 0;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (delta != 0) {
			for (int i = 0; i < doubleBoxes.size(); i++) {
				EditBox box = doubleBoxes.get(i);
				if (box.visible && box.isMouseOver(mouseX, mouseY)) {
					String key = doubleKeys.get(i);
					double value = dbl(key) + (delta > 0 ? 1 : -1);
					condition.params().putDouble(key, value);
					box.setValue(format(value));
					return true;
				}
			}
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!suggestions.isEmpty() && suggestionBox != null && suggestionBox.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				acceptSuggestion();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_DOWN) {
				suggestionIndex = (suggestionIndex + 1) % suggestions.size();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_UP) {
				suggestionIndex = (suggestionIndex + suggestions.size() - 1) % suggestions.size();
				return true;
			}
		}
		boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
		refreshSuggestions();
		return handled;
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		boolean handled = super.charTyped(codePoint, modifiers);
		refreshSuggestions();
		return handled;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && !suggestions.isEmpty() && suggestionBox != null && suggestionBox.visible) {
			for (int i = 0; i < suggestions.size(); i++) {
				int rowTop = suggestionBox.getY() + suggestionBox.getHeight() + i * SUGGESTION_ROW;
				if (mouseX >= suggestionBox.getX() && mouseX < suggestionBox.getX() + suggestionBox.getWidth()
						&& mouseY >= rowTop && mouseY < rowTop + SUGGESTION_ROW) {
					suggestionIndex = i;
					acceptSuggestion();
					return true;
				}
			}
		}
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		refreshSuggestions();
		return handled;
	}

	private void acceptSuggestion() {
		if (suggestions.isEmpty() || suggestionBox == null || suggestParamKey == null) {
			return;
		}
		String pick = suggestions.get(Math.min(suggestionIndex, suggestions.size() - 1));
		if (suggestStart >= 0) {
			String current = suggestionBox.getValue();
			int cut = Math.max(0, Math.min(suggestStart, current.length()));
			String completed = current.substring(0, cut) + pick;
			suggestionBox.setValue(completed);
			condition.params().putString(suggestParamKey, completed);
		} else {
			suggestionBox.setValue(pick);
			condition.params().putString(suggestParamKey, pick);
		}
		suggestionBox.moveCursorToEnd();
		refreshSuggestions();
	}

	private void refreshSuggestions() {
		if (suggestionBox == null || !suggestionBox.visible || !suggestionBox.isFocused()) {
			suggestions = List.of();
			return;
		}
		if ("command".equals(suggestKey)) {
			String current = suggestionBox.getValue();
			if (!current.equals(lastSuggestRequest)) {
				lastSuggestRequest = current;
				ConditionTestNet.requestSuggestions(current);
			}
			suggestions = serverSuggestions.size() > MAX_SUGGESTIONS ? serverSuggestions.subList(0, MAX_SUGGESTIONS) : serverSuggestions;
			if (suggestionIndex >= suggestions.size()) {
				suggestionIndex = 0;
			}
			return;
		}
		String current = suggestionBox.getValue().trim().toLowerCase(Locale.ROOT);
		List<String> matches = new ArrayList<>();
		for (String option : completeOptions()) {
			String lower = option.toLowerCase(Locale.ROOT);
			int separator = lower.indexOf(':');
			String path = separator >= 0 ? lower.substring(separator + 1) : lower;
			if (current.isEmpty() || lower.startsWith(current) || path.startsWith(current)) {
				matches.add(option);
			}
		}
		matches.sort(String::compareToIgnoreCase);
		if (matches.size() == 1 && matches.get(0).equalsIgnoreCase(suggestionBox.getValue().trim())) {
			matches = List.of();
		}
		suggestions = matches.size() > MAX_SUGGESTIONS ? matches.subList(0, MAX_SUGGESTIONS) : matches;
		if (suggestionIndex >= suggestions.size()) {
			suggestionIndex = 0;
		}
	}

	private List<String> completeOptions() {
		String key = suggestKey == null ? "" : suggestKey;
		return switch (key) {
			case "player" -> playerOptions();
			case "biome" -> biomeOptions();
			case "entity" -> entityOptions();
			default -> List.of();
		};
	}

	private List<String> playerOptions() {
		List<String> names = new ArrayList<>(List.of("@a", "@p", "@r", "@s", "@e"));
		Minecraft client = this.minecraft;
		if (client != null && client.getConnection() != null) {
			for (var info : client.getConnection().getOnlinePlayers()) {
				names.add(info.getProfile().getName());
			}
		}
		return names;
	}

	private List<String> biomeOptions() {
		Minecraft client = this.minecraft;
		if (client == null || client.level == null) {
			return List.of();
		}
		return client.level.registryAccess().registryOrThrow(Registries.BIOME).keySet().stream().map(Object::toString).sorted().toList();
	}

	private List<String> entityOptions() {
		return BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString).sorted().toList();
	}

	private void renderSuggestions(GuiGraphics guiGraphics) {
		if (suggestions.isEmpty() || suggestionBox == null || !suggestionBox.visible) {
			return;
		}
		int x = suggestionBox.getX();
		int y = suggestionBox.getY() + suggestionBox.getHeight();
		int width = suggestionBox.getWidth();
		guiGraphics.fill(x, y, x + width, y + suggestions.size() * SUGGESTION_ROW + 2, 0xF0000000);
		for (int i = 0; i < suggestions.size(); i++) {
			int rowTop = y + i * SUGGESTION_ROW;
			if (i == suggestionIndex) {
				guiGraphics.fill(x, rowTop, x + width, rowTop + SUGGESTION_ROW, 0xFF404040);
			}
			String shown = this.font.plainSubstrByWidth(suggestions.get(i), width - 6, false);
			guiGraphics.drawString(this.font, shown, x + 3, rowTop + 1, 0xFFFFFF, false);
		}
		guiGraphics.renderOutline(x, y, width, suggestions.size() * SUGGESTION_ROW + 2, 0xFF6A6A6A);
	}

	private static String title(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
	}

	private void saveAndClose() {
		block.getQueues().get(queueIndex).getRuleRoot().replaceByIdentity(original, condition.copy());
		MusicBlockScreen.sendUpdate(block);
		this.minecraft.setScreen(parent);
	}

	private static String format(double value) {
		if (value == Math.rint(value)) {
			return String.valueOf((long) value);
		}
		return String.valueOf(value);
	}

	private static double parse(String text, double fallback) {
		try {
			return Double.parseDouble(text.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		this.renderBackground(guiGraphics);
		int leftPos = (this.width - bgWidth) / 2;
		int topPos = (this.height - bgHeight) / 2;
		guiGraphics.fill(leftPos, topPos, leftPos + bgWidth, topPos + bgHeight, 0xF0101010);
		guiGraphics.renderOutline(leftPos, topPos, bgWidth, bgHeight, GuiIcons.boxOutlineColor(block.getBlockPos(), block.getOutlineColor()));
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + GuiLayout.TITLE_TOP, 0xFFFFFF);
		int labelX = leftPos + GuiLayout.SCREEN_PADDING;
		if (testStatus != null && testStatusY >= 0) {
			guiGraphics.drawString(this.font, testStatus, labelX, testStatusY + 5, testStatusColor, false);
		}
		for (int i = 0; i < notes.size(); i++) {
			String shown = this.font.plainSubstrByWidth(notes.get(i), bgWidth - 2 * GuiLayout.SCREEN_PADDING, false);
			guiGraphics.drawString(this.font, shown, labelX, notesY + i * NOTE_ROW, 0x9A9A9A, false);
		}

		super.render(guiGraphics, mouseX, mouseY, delta);
		renderSuggestions(guiGraphics);
	}
}
