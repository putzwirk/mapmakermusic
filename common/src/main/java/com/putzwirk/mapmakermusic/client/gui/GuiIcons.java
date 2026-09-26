package com.putzwirk.mapmakermusic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;

public final class GuiIcons {

	public static final int ROW_GLYPH = 14;
	public static final int SPIN_H = 7;

	public static final String BACK = "\u2190";
	public static final String ADD = "+";
	public static final String UP = "\u25B2";
	public static final String DOWN = "\u25BC";
	public static final String REMOVE = "\u00D7";
	public static final String NOTE_MUSIC = "\u266A ";
	public static final String NOTE_SOUND = "\u266B ";

	private GuiIcons() {
	}

	public static void drawRowGlyph(GuiGraphics guiGraphics, Font font, int x, int y, String symbol, boolean hovered) {
		int face = hovered ? 0xFF9BA3B0 : 0xFF8B8B8B;
		guiGraphics.fill(x, y, x + ROW_GLYPH, y + ROW_GLYPH, 0xFF000000);
		guiGraphics.fill(x + 1, y + 1, x + ROW_GLYPH - 1, y + ROW_GLYPH - 1, face);
		guiGraphics.fill(x + 1, y + 1, x + ROW_GLYPH - 1, y + 2, 0xFFD6D6D6);
		guiGraphics.fill(x + 1, y + ROW_GLYPH - 2, x + ROW_GLYPH - 1, y + ROW_GLYPH - 1, 0xFF4A4A4A);
		guiGraphics.drawCenteredString(font, symbol, x + ROW_GLYPH / 2, y + 3, 0xFFFFFF);
	}

	public static boolean inGlyph(double mouseX, double mouseY, int x, int y) {
		return mouseX >= x && mouseX < x + ROW_GLYPH && mouseY >= y && mouseY < y + ROW_GLYPH;
	}

	public static void drawSpinButton(GuiGraphics guiGraphics, Font font, int x, int y, String symbol, boolean hovered, boolean enabled, int textDy) {
		int face = !enabled ? 0xFF3A3A3A : hovered ? 0xFF9BA3B0 : 0xFF8B8B8B;
		int text = enabled ? 0xFFFFFF : 0xFF707070;
		guiGraphics.fill(x, y, x + ROW_GLYPH, y + SPIN_H, 0xFF000000);
		guiGraphics.fill(x + 1, y + 1, x + ROW_GLYPH - 1, y + SPIN_H - 1, face);
		guiGraphics.drawCenteredString(font, symbol, x + ROW_GLYPH / 2, y + 1 + textDy, text);
	}

	public static boolean inSpin(double mouseX, double mouseY, int x, int y) {
		return mouseX >= x && mouseX < x + ROW_GLYPH && mouseY >= y && mouseY < y + SPIN_H;
	}

	public static int boxOutlineColor(BlockPos pos) {
		return boxOutlineColor(pos, null);
	}

	public static int boxOutlineColor(BlockPos pos, Integer custom) {
		if (custom != null) {
			return 0xFF000000 | (custom & 0xFFFFFF);
		}
		long hash = pos.asLong() * 0x9E3779B97F4A7C15L;
		float hue = (float) (((hash >>> 16) % 360 + 360) % 360) / 360f;
		return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.85f, 1f);
	}

	public static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
