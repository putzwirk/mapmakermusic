package com.putzwirk.mapmakermusic.client.gui;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import com.putzwirk.mapmakermusic.block.MusicCondition;
import com.putzwirk.mapmakermusic.client.gui.GuiIcons;
import com.putzwirk.mapmakermusic.client.gui.MusicBlockScreen;
import java.util.List;
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
	private final int conditionIndex;
	private final MusicCondition condition;

	private Button modeBtn;
	private EditBox textEdit;
	private EditBox minEdit;
	private EditBox maxEdit;

	public MusicConditionScreen(Screen parent, MusicBlockEntity block, int queueIndex, int conditionIndex) {
		super(Component.literal(titleFor(block, queueIndex, conditionIndex)));
		this.parent = parent;
		this.block = block;
		this.queueIndex = queueIndex;
		this.conditionIndex = conditionIndex;

		MusicCondition existing = conditionIndex >= 0 && conditionIndex < block.getQueues().get(queueIndex).getConditions().size()
				? block.getQueues().get(queueIndex).getConditions().get(conditionIndex)
				: null;
		this.condition = existing != null ? existing.copy() : MusicCondition.Type.TIME.newDefault();
	}

	private static String titleFor(MusicBlockEntity block, int queueIndex, int conditionIndex) {
		if (conditionIndex >= 0 && conditionIndex < block.getQueues().get(queueIndex).getConditions().size()) {
			return block.getQueues().get(queueIndex).getConditions().get(conditionIndex).getType().displayName();
		}
		return "New Condition";
	}

	@Override
	protected void init() {
		super.init();

		int leftPos = (this.width - BG_WIDTH) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		int x = leftPos + 12;

		addRenderableWidget(Button.builder(Component.literal(GuiIcons.BACK), b -> {
			MusicBlockScreen.sendUpdate(block);
			this.minecraft.setScreen(parent);
		}).tooltip(Tooltip.create(Component.literal("Back"))).bounds(x, topPos + 8, 18, 18).build());

		this.modeBtn = Button.builder(modeLabel(), b -> {
			condition.getType().cycleMode(condition);
			this.rebuildWidgets();
		}).bounds(x, topPos + 30, 224, 18).build();
		addRenderableWidget(modeBtn);

		this.textEdit = new EditBox(this.font, x, topPos + 52, 224, 18, Component.literal("Value"));
		String hint = condition.getType().textHint();
		this.textEdit.setHint(Component.literal(hint == null ? "value" : hint));
		this.textEdit.setValue(condition.getText());
		this.textEdit.setResponder(condition::setText);
		addRenderableWidget(textEdit);

		this.minEdit = new EditBox(this.font, x, topPos + 74, 108, 18, Component.literal("Min"));
		this.minEdit.setValue(format(condition.getMin()));
		this.minEdit.setResponder(text -> condition.setMin(parse(text, condition.getMin())));
		this.minEdit.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
		addRenderableWidget(minEdit);

		this.maxEdit = new EditBox(this.font, x + 116, topPos + 74, 108, 18, Component.literal("Max"));
		this.maxEdit.setValue(format(condition.getMax()));
		this.maxEdit.setResponder(text -> condition.setMax(parse(text, condition.getMax())));
		this.maxEdit.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
		addRenderableWidget(maxEdit);

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> saveAndClose())
				.bounds(x, topPos + BG_HEIGHT - 24, 224, 18).build());

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
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	private void updateVisibility() {
		MusicCondition.Type type = condition.getType();
		modeBtn.visible = !type.modes().isEmpty();
		textEdit.visible = type.textHint() != null;
		boolean range = type.hasRange() && (type != MusicCondition.Type.TIME || condition.getText().equalsIgnoreCase("range"));
		minEdit.visible = range;
		maxEdit.visible = range;
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
		MusicCondition saved = condition.copy();
		if (conditionIndex >= 0) {
			List<MusicCondition> conditions = block.getQueues().get(queueIndex).getConditions();
			if (conditionIndex < conditions.size()) {
				conditions.set(conditionIndex, saved);
			}
		} else {
			block.getQueues().get(queueIndex).getConditions().add(saved);
		}
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
		int leftPos = (this.width - BG_WIDTH) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		guiGraphics.fill(leftPos, topPos, leftPos + BG_WIDTH, topPos + BG_HEIGHT, 0xF0101010);
		guiGraphics.renderOutline(leftPos, topPos, BG_WIDTH, BG_HEIGHT, GuiIcons.boxOutlineColor(block.getBlockPos()));
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + 12, 0xFFFFFF);

		super.render(guiGraphics, mouseX, mouseY, delta);
	}
}
