package com.putzwirk.mapmakermusic.client.gui;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import com.putzwirk.mapmakermusic.client.gui.GuiIcons;
import com.putzwirk.mapmakermusic.client.gui.MusicBlockScreen;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MusicConditionScreen extends Screen {

	private static final int BG_WIDTH = 248;
	private static final int BG_HEIGHT = 150;

	private final Screen parent;
	private final MusicBlockEntity block;
	private final int queueIndex;
	private final MusicCondition original;
	private final MusicCondition condition;

	private Button modeBtn;
	private EditBox textEdit;
	private EditBox minEdit;
	private EditBox maxEdit;
	private final EditBox[] coordEdits = new EditBox[6];
	private static final String[] COORD_HINTS = {"X1", "X2", "Y1", "Y2", "Z1", "Z2"};

	public MusicConditionScreen(Screen parent, MusicBlockEntity block, int queueIndex, MusicCondition condition) {
		super(Component.literal(condition.getType().displayName()));
		this.parent = parent;
		this.block = block;
		this.queueIndex = queueIndex;
		this.original = condition;
		this.condition = condition.copy();
	}

	@Override
	protected void init() {
		super.init();

		int leftPos = (this.width - BG_WIDTH) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		int x = leftPos + GuiLayout.SCREEN_PADDING;
		int contentWidth = BG_WIDTH - 2 * GuiLayout.SCREEN_PADDING;
		int fieldWidth = (contentWidth - GuiLayout.WIDGET_SPACING) / 2;

		addRenderableWidget(Button.builder(Component.literal(GuiIcons.BACK), b -> {
			MusicBlockScreen.sendUpdate(block);
			this.minecraft.setScreen(parent);
		}).tooltip(Tooltip.create(Component.literal("Back"))).bounds(x, topPos + GuiLayout.BACK_TOP, GuiLayout.BACK_SIZE, GuiLayout.BACK_SIZE).build());

		this.modeBtn = Button.builder(modeLabel(), b -> {
			condition.getType().cycleMode(condition);
			this.rebuildWidgets();
		}).bounds(x, topPos + 30, contentWidth, GuiLayout.BUTTON_HEIGHT).build();
		addRenderableWidget(modeBtn);

		this.textEdit = new EditBox(this.font, x, topPos + 30 + GuiLayout.SECTION_SPACING, contentWidth, GuiLayout.BUTTON_HEIGHT, Component.literal("Value"));
		String hint = condition.getType().textHint();
		this.textEdit.setHint(Component.literal(hint == null ? "value" : hint));
		this.textEdit.setValue(condition.getText());
		this.textEdit.setResponder(condition::setText);
		addRenderableWidget(textEdit);

		this.minEdit = new EditBox(this.font, x, topPos + 30 + 2 * GuiLayout.SECTION_SPACING, fieldWidth, GuiLayout.BUTTON_HEIGHT, Component.literal("Min"));
		this.minEdit.setValue(format(condition.getMin()));
		this.minEdit.setResponder(text -> condition.setMin(parse(text, condition.getMin())));
		this.minEdit.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
		addRenderableWidget(minEdit);

		this.maxEdit = new EditBox(this.font, x + fieldWidth + GuiLayout.WIDGET_SPACING, topPos + 30 + 2 * GuiLayout.SECTION_SPACING, fieldWidth, GuiLayout.BUTTON_HEIGHT, Component.literal("Max"));
		this.maxEdit.setValue(format(condition.getMax()));
		this.maxEdit.setResponder(text -> condition.setMax(parse(text, condition.getMax())));
		this.maxEdit.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
		addRenderableWidget(maxEdit);

		for (int i = 0; i < 6; i++) {
			final int bound = i;
			EditBox coord = new EditBox(this.font, x + (i % 2) * (fieldWidth + GuiLayout.WIDGET_SPACING), topPos + 30 + (i / 2) * GuiLayout.SECTION_SPACING, fieldWidth, GuiLayout.BUTTON_HEIGHT, Component.literal(COORD_HINTS[i]));
			coord.setHint(Component.literal(COORD_HINTS[i]));
			coord.setValue(formatBound(condition.getBound(bound)));
			coord.setResponder(text -> condition.setBound(bound, parseBound(text, condition.getBound(bound))));
			coord.setTooltip(Tooltip.create(Component.literal("Empty ignores this bound. Scroll to adjust")));
			this.coordEdits[i] = coord;
			addRenderableWidget(coord);
		}

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> saveAndClose())
				.bounds(x, topPos + BG_HEIGHT - GuiLayout.BOTTOM_OFFSET, contentWidth, GuiLayout.BUTTON_HEIGHT).build());

		updateVisibility();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (delta != 0 && minEdit.visible) {
			if (minEdit.isMouseOver(mouseX, mouseY)) {
				condition.setMin(condition.getMin() + (delta > 0 ? 1 : -1));
				minEdit.setValue(format(condition.getMin()));
				return true;
			}
			if (maxEdit.isMouseOver(mouseX, mouseY)) {
				condition.setMax(condition.getMax() + (delta > 0 ? 1 : -1));
				maxEdit.setValue(format(condition.getMax()));
				return true;
			}
		}
		if (delta != 0 && coordEdits[0].visible) {
			for (int i = 0; i < 6; i++) {
				if (coordEdits[i].isMouseOver(mouseX, mouseY)) {
					double current = condition.getBound(i);
					if (Double.isNaN(current)) {
						current = 0;
					}
					condition.setBound(i, current + (delta > 0 ? 1 : -1));
					coordEdits[i].setValue(formatBound(condition.getBound(i)));
					return true;
				}
			}
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	private void updateVisibility() {
		MusicCondition.Type type = condition.getType();
		modeBtn.visible = !type.modes().isEmpty();
		textEdit.visible = type.textHint() != null;
		boolean range = type.hasRange() && (type != MusicCondition.Type.TIME || condition.getText().equalsIgnoreCase("range"));
		boolean count = type == MusicCondition.Type.ENTITY_ALIVE;
		minEdit.setHint(Component.literal(count ? "count" : "Min"));
		minEdit.visible = range || count;
		maxEdit.visible = range;
		boolean coords = type == MusicCondition.Type.COORDINATES;
		for (EditBox coord : coordEdits) {
			coord.visible = coords;
		}
	}

	private Component modeLabel() {
		return Component.literal("Mode: " + title(condition.getType().modeOf(condition)));
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
	}
}
