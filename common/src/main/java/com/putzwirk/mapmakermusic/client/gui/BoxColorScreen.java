package com.putzwirk.mapmakermusic.client.gui;

import com.putzwirk.mapmakermusic.block.MusicBlockEntity;
import java.awt.Color;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BoxColorScreen extends Screen {

	private static final int BG_WIDTH = 264;
	private static final int BG_HEIGHT = 232;
	private static final int[] PRESETS = {
			0xFFFFFF, 0xFFE93D, 0xFF8C1A, 0xFF5555, 0xFF5B9E, 0xB45BFF,
			0x5B7CFF, 0x4BD8FF, 0x5BFFD8, 0x4BE08A, 0x9DFF4B, 0x8A8A8A
	};

	private final Screen parent;
	private final MusicBlockEntity block;

	private int hue;
	private int saturation;
	private int brightness;
	private boolean auto;

	private HsbSlider hueSlider;
	private HsbSlider saturationSlider;
	private HsbSlider brightnessSlider;
	private EditBox hexEdit;
	private int presetX;
	private int presetY;

	public BoxColorScreen(Screen parent, MusicBlockEntity block) {
		super(Component.literal("Outline color"));
		this.parent = parent;
		this.block = block;
		Integer custom = block.getOutlineColor();
		if (custom == null) {
			this.auto = true;
			setFromPacked(GuiIcons.boxOutlineColor(block.getBlockPos(), null));
		} else {
			setFromPacked(0xFF000000 | custom);
		}
	}

	@Override
	protected void init() {
		super.init();

		int leftPos = (this.width - BG_WIDTH) / 2;
		int topPos = (this.height - BG_HEIGHT) / 2;
		int x = leftPos + GuiLayout.SCREEN_PADDING;
		int contentWidth = BG_WIDTH - 2 * GuiLayout.SCREEN_PADDING;

		this.hueSlider = new HsbSlider(x, topPos + 56, contentWidth, "Hue", 360, hue, v -> {
			hue = v;
			auto = false;
			syncHex();
		});
		addRenderableWidget(hueSlider);

		this.saturationSlider = new HsbSlider(x, topPos + 56 + GuiLayout.SECTION_SPACING, contentWidth, "Saturation", 100, saturation, v -> {
			saturation = v;
			auto = false;
			syncHex();
		});
		addRenderableWidget(saturationSlider);

		this.brightnessSlider = new HsbSlider(x, topPos + 56 + 2 * GuiLayout.SECTION_SPACING, contentWidth, "Brightness", 100, brightness, v -> {
			brightness = v;
			auto = false;
			syncHex();
		});
		addRenderableWidget(brightnessSlider);

		this.hexEdit = new EditBox(this.font, x, topPos + 126, 96, GuiLayout.BUTTON_HEIGHT, Component.literal("Hex"));
		this.hexEdit.setMaxLength(7);
		this.hexEdit.setValue(toHex(packed()));
		this.hexEdit.setResponder(text -> {
			Integer parsed = parseHex(text);
			if (parsed != null) {
				setFromPacked(0xFF000000 | parsed);
				auto = false;
				syncSliders();
			}
		});
		addRenderableWidget(hexEdit);

		int smallWidth = (contentWidth - 96 - 2 * GuiLayout.WIDGET_SPACING) / 2;
		addRenderableWidget(Button.builder(Component.literal("Random"), b -> {
			hue = this.minecraft.level.random.nextInt(360);
			saturation = 85;
			brightness = 100;
			auto = false;
			syncSliders();
			syncHex();
		}).bounds(x + 96 + GuiLayout.WIDGET_SPACING, topPos + 126, smallWidth, GuiLayout.BUTTON_HEIGHT).build());

		addRenderableWidget(Button.builder(Component.literal("Auto"), b -> {
			auto = true;
			setFromPacked(GuiIcons.boxOutlineColor(block.getBlockPos(), null));
			syncSliders();
			syncHex();
		}).bounds(x + 96 + GuiLayout.WIDGET_SPACING + smallWidth + GuiLayout.WIDGET_SPACING, topPos + 126, smallWidth, GuiLayout.BUTTON_HEIGHT).build());

		this.presetX = x;
		this.presetY = topPos + 152;

		int halfWidth = (contentWidth - GuiLayout.WIDGET_SPACING) / 2;
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> saveAndClose())
				.bounds(x, topPos + BG_HEIGHT - GuiLayout.BOTTOM_OFFSET, halfWidth, GuiLayout.BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(x + halfWidth + GuiLayout.WIDGET_SPACING, topPos + BG_HEIGHT - GuiLayout.BOTTOM_OFFSET, halfWidth, GuiLayout.BUTTON_HEIGHT).build());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (int i = 0; i < PRESETS.length; i++) {
			int sx = presetX + i * 20;
			if (mouseX >= sx && mouseX < sx + 18 && mouseY >= presetY && mouseY < presetY + 18) {
				setFromPacked(0xFF000000 | PRESETS[i]);
				auto = false;
				syncSliders();
				syncHex();
				GuiIcons.click();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void saveAndClose() {
		block.setOutlineColor(auto ? null : packed() & 0xFFFFFF);
		MusicBlockScreen.sendUpdate(block);
		onClose();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	private int packed() {
		return 0xFF000000 | Color.HSBtoRGB(hue / 360f, saturation / 100f, brightness / 100f);
	}

	private void setFromPacked(int argb) {
		float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
		hue = Math.round(hsb[0] * 360f) % 360;
		saturation = Math.round(hsb[1] * 100f);
		brightness = Math.round(hsb[2] * 100f);
	}

	private void syncSliders() {
		hueSlider.sync(hue);
		saturationSlider.sync(saturation);
		brightnessSlider.sync(brightness);
	}

	private void syncHex() {
		if (!hexEdit.getValue().equalsIgnoreCase(toHex(packed()))) {
			hexEdit.setValue(toHex(packed()));
		}
	}

	private static String toHex(int argb) {
		return String.format("%06X", argb & 0xFFFFFF);
	}

	private static Integer parseHex(String text) {
		String clean = text.trim();
		if (clean.startsWith("#")) {
			clean = clean.substring(1);
		}
		if (clean.length() != 6) {
			return null;
		}
		try {
			return Integer.parseUnsignedInt(clean, 16);
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
		guiGraphics.renderOutline(leftPos, topPos, BG_WIDTH, BG_HEIGHT, packed());
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + GuiLayout.TITLE_TOP, 0xFFFFFF);

		int color = auto ? GuiIcons.boxOutlineColor(block.getBlockPos(), null) : packed();
		guiGraphics.fill(leftPos + GuiLayout.SCREEN_PADDING, topPos + 28, leftPos + BG_WIDTH - GuiLayout.SCREEN_PADDING, topPos + 48, 0xFF000000);
		guiGraphics.fill(leftPos + GuiLayout.SCREEN_PADDING + 1, topPos + 29, leftPos + BG_WIDTH - GuiLayout.SCREEN_PADDING - 1, topPos + 47, color);
		if (auto) {
			guiGraphics.drawString(this.font, "Auto", leftPos + 16, topPos + 34, 0xFFFFFF, false);
		}

		for (int i = 0; i < PRESETS.length; i++) {
			int sx = presetX + i * 20;
			boolean hovered = mouseX >= sx && mouseX < sx + 18 && mouseY >= presetY && mouseY < presetY + 18;
			guiGraphics.fill(sx, presetY, sx + 18, presetY + 18, 0xFF000000);
			guiGraphics.fill(sx + 1, presetY + 1, sx + 17, presetY + 17, 0xFF000000 | PRESETS[i]);
			if (hovered) {
				guiGraphics.renderOutline(sx, presetY, 18, 18, 0xFFFFFFFF);
			}
		}

		super.render(guiGraphics, mouseX, mouseY, delta);
	}

	private static class HsbSlider extends AbstractSliderButton {
		private final String name;
		private final int max;
		private final IntConsumer onChange;

		HsbSlider(int x, int y, int width, String name, int max, int current, IntConsumer onChange) {
			super(x, y, width, GuiLayout.BUTTON_HEIGHT, Component.literal(""), (double) current / max);
			this.name = name;
			this.max = max;
			this.onChange = onChange;
			updateMessage();
		}

		void sync(int current) {
			this.value = (double) current / max;
			updateMessage();
		}

		int current() {
			return (int) Math.round(this.value * max);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(name + ": " + current()));
		}

		@Override
		protected void applyValue() {
			updateMessage();
			onChange.accept(current());
		}
	}
}
