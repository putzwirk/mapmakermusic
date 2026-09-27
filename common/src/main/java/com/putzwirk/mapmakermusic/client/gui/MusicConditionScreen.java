package com.putzwirk.mapmakermusic.client.gui;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import com.putzwirk.mapmakermusic.block.condition.ConditionKind;
import com.putzwirk.mapmakermusic.block.condition.CoordinatesConditionKind;
import com.putzwirk.mapmakermusic.block.condition.FieldSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

	private static final int BG_WIDTH = 248;
	private static final int BG_HEIGHT = 150;
	private static final int MAX_SUGGESTIONS = 8;
	private static final int SUGGESTION_ROW = 11;

	private final Screen parent;
	private final MusicBlockEntity block;
	private final int queueIndex;
	private final MusicCondition original;
	private final MusicCondition condition;

	private EditBox suggestionBox;
	private String suggestParamKey;
	private String suggestKey;
	private final List<EditBox> doubleBoxes = new ArrayList<>();
	private final List<String> doubleKeys = new ArrayList<>();
	private final EditBox[] coordEdits = new EditBox[6];
	private List<String> suggestions = List.of();
	private int suggestionIndex;

	public MusicConditionScreen(Screen parent, MusicBlockEntity block, int queueIndex, MusicCondition condition) {
		super(Component.literal(kindOf(condition) == null ? "Unknown" : kindOf(condition).displayName()));
		this.parent = parent;
		this.block = block;
		this.queueIndex = queueIndex;
		this.original = condition;
		this.condition = condition.copy();
	}

	private static ConditionKind kindOf(MusicCondition condition) {
		return condition.kind();
	}

	@Override
	protected void init() {
		super.init();
		doubleBoxes.clear();
		doubleKeys.clear();
		suggestionBox = null;
		suggestParamKey = null;
		suggestKey = null;
		for (int i = 0; i < 6; i++) {
			coordEdits[i] = null;
		}

		int leftPos = (this.width - BG_WIDTH) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		int x = leftPos + GuiLayout.SCREEN_PADDING;
		int contentWidth = BG_WIDTH - 2 * GuiLayout.SCREEN_PADDING;
		int fieldWidth = (contentWidth - GuiLayout.WIDGET_SPACING) / 2;

		addRenderableWidget(Button.builder(Component.literal(GuiIcons.BACK), b -> {
			MusicBlockScreen.sendUpdate(block);
			this.minecraft.setScreen(parent);
		}).tooltip(Tooltip.create(Component.literal("Back"))).bounds(x, topPos + GuiLayout.BACK_TOP, GuiLayout.BACK_SIZE, GuiLayout.BACK_SIZE).build());

		ConditionKind kind = condition.kind();
		int row = 0;
		if (kind != null) {
			for (FieldSpec spec : kind.editorFields()) {
				if (!kind.fieldVisible(spec, condition)) {
					continue;
				}
				row += addField(kind, spec, x, topPos + 30 + row * GuiLayout.SECTION_SPACING, contentWidth, fieldWidth);
			}
		}

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> saveAndClose())
				.bounds(x, topPos + BG_HEIGHT - GuiLayout.BOTTOM_OFFSET, contentWidth, GuiLayout.BUTTON_HEIGHT).build());
	}

	private int addField(ConditionKind kind, FieldSpec spec, int x, int y, int contentWidth, int fieldWidth) {
		return switch (spec.type()) {
			case MODE_CYCLE -> {
				addModeButton(kind, x, y, contentWidth);
				yield 1;
			}
			case TEXT, ENTITY_ID -> {
				addTextBox(spec, x, y, contentWidth, false);
				yield 1;
			}
			case LONG_TEXT -> {
				addTextBox(spec, x, y, contentWidth, true);
				yield 2;
			}
			case NUMBER -> {
				addNumberBox(spec, x, y, contentWidth);
				yield 1;
			}
			case RANGE -> {
				addRangeBoxes(spec, x, y, fieldWidth);
				yield 1;
			}
			case BOUNDS -> {
				addBoundBoxes(x, y, fieldWidth);
				yield 3;
			}
			case ACTION -> {
				addActionButton(kind, spec, x, y, contentWidth);
				yield 1;
			}
		};
	}

	private void addModeButton(ConditionKind kind, int x, int y, int contentWidth) {
		addRenderableWidget(Button.builder(Component.literal("Mode: " + title(kind.modeOf(condition))), b -> {
			kind.cycleMode(condition);
			this.rebuildWidgets();
		}).bounds(x, y, contentWidth, GuiLayout.BUTTON_HEIGHT).build());
	}

	private void addTextBox(FieldSpec spec, int x, int y, int contentWidth, boolean tall) {
		int height = tall ? 2 * GuiLayout.SECTION_SPACING - 4 : GuiLayout.BUTTON_HEIGHT;
		EditBox box = new EditBox(this.font, x, y, contentWidth, height, Component.literal(spec.label()));
		box.setHint(Component.literal(spec.hint() == null ? "value" : spec.hint()));
		box.setMaxLength(tall ? 512 : 128);
		box.setValue(condition.params().getString(spec.key()));
		box.setResponder(text -> {
			condition.params().putString(spec.key(), text);
			if (box == suggestionBox) {
				refreshSuggestions();
			}
		});
		addRenderableWidget(box);
		if (spec.type() == FieldSpec.FieldType.ENTITY_ID && suggestionBox == null) {
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

	private void addBoundBoxes(int x, int y, int fieldWidth) {
		for (int i = 0; i < 6; i++) {
			final int bound = i;
			EditBox coord = new EditBox(this.font, x + (i % 2) * (fieldWidth + GuiLayout.WIDGET_SPACING), y + (i / 2) * GuiLayout.SECTION_SPACING, fieldWidth, GuiLayout.BUTTON_HEIGHT,
					Component.literal(CoordinatesConditionKind.BOUND_HINTS[i]));
			coord.setHint(Component.literal(CoordinatesConditionKind.BOUND_HINTS[i]));
			coord.setValue(formatBound(CoordinatesConditionKind.bound(condition.params(), bound)));
			coord.setResponder(text -> {
				double fallback = CoordinatesConditionKind.bound(condition.params(), bound);
				double value = parseBound(text, fallback);
				if (Double.isNaN(value)) {
					condition.params().remove(CoordinatesConditionKind.BOUND_KEYS[bound]);
				} else {
					condition.params().putDouble(CoordinatesConditionKind.BOUND_KEYS[bound], value);
				}
			});
			coord.setTooltip(Tooltip.create(Component.literal("Empty ignores this bound. Scroll to adjust")));
			this.coordEdits[i] = coord;
			addRenderableWidget(coord);
		}
	}

	private void addActionButton(ConditionKind kind, FieldSpec spec, int x, int y, int contentWidth) {
		addRenderableWidget(Button.builder(Component.literal(spec.label()), b -> {
			String result = kind.runAction(spec.key(), condition, this.minecraft);
			b.setMessage(Component.literal(result == null ? spec.label() : spec.label() + ": " + result));
		}).bounds(x, y, contentWidth, GuiLayout.BUTTON_HEIGHT).build());
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
			for (int i = 0; i < 6; i++) {
				EditBox coord = coordEdits[i];
				if (coord != null && coord.visible && coord.isMouseOver(mouseX, mouseY)) {
					double current = CoordinatesConditionKind.bound(condition.params(), i);
					if (Double.isNaN(current)) {
						current = 0;
					}
					double value = current + (delta > 0 ? 1 : -1);
					condition.params().putDouble(CoordinatesConditionKind.BOUND_KEYS[i], value);
					coord.setValue(formatBound(value));
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
		suggestionBox.setValue(pick);
		condition.params().putString(suggestParamKey, pick);
		suggestionBox.moveCursorToEnd();
		refreshSuggestions();
	}

	private void refreshSuggestions() {
		if (suggestionBox == null || !suggestionBox.visible || !suggestionBox.isFocused()) {
			suggestions = List.of();
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

	private static String formatBound(double value) {
		return Double.isNaN(value) ? "" : format(value);
	}

	private static double parse(String text, double fallback) {
		try {
			return Double.parseDouble(text.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static double parseBound(String text, double fallback) {
		if (text == null || text.trim().isEmpty()) {
			return Double.NaN;
		}
		return parse(text, fallback);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		this.renderBackground(guiGraphics);
		int leftPos = (this.width - BG_WIDTH) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		guiGraphics.fill(leftPos, topPos, leftPos + BG_WIDTH, topPos + BG_HEIGHT, 0xF0101010);
		guiGraphics.renderOutline(leftPos, topPos, BG_WIDTH, BG_HEIGHT, GuiIcons.boxOutlineColor(block.getBlockPos(), block.getOutlineColor()));
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + GuiLayout.TITLE_TOP, 0xFFFFFF);

		super.render(guiGraphics, mouseX, mouseY, delta);
		renderSuggestions(guiGraphics);
	}
}
