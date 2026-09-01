package dev.duetigh.arashi.gui.theme;

import net.minecraft.resources.Identifier;

/**
 * Shared dark palette and metrics for every custom Arashi screen/widget - one theme, applied
 * consistently, instead of each screen picking its own colors.
 */
public final class ArashiTheme {

	public static final int BACKGROUND = 0xFF17181C;
	public static final int PANEL = 0xFF1F2126;
	public static final int CARD = 0xFF272A31;
	public static final int CARD_HOVER = 0xFF2F333B;
	public static final int BORDER = 0xFF35383F;

	public static final int TEXT_PRIMARY = 0xFFF2F1ED;
	public static final int TEXT_SECONDARY = 0xFFA6ABB5;
	public static final int TEXT_DISABLED = 0xFF6B707A;

	public static final int ACCENT = 0xFFF2B84F;
	public static final int ACCENT_HOVER = 0xFFF5C875;
	public static final int ACCENT_PRESSED = 0xFFD9A23D;
	public static final int ACCENT_TEXT = 0xFF201A0F;

	public static final int DANGER = 0xFFE0664F;

	public static final int CORNER_RADIUS = 8;
	public static final int PADDING = 10;
	public static final int GAP = 6;

	public static final Identifier CORNER_TOP_LEFT = widgetTexture("corner_tl.png");
	public static final Identifier CORNER_TOP_RIGHT = widgetTexture("corner_tr.png");
	public static final Identifier CORNER_BOTTOM_LEFT = widgetTexture("corner_bl.png");
	public static final Identifier CORNER_BOTTOM_RIGHT = widgetTexture("corner_br.png");

	private static Identifier widgetTexture(String fileName) {
		return Identifier.fromNamespaceAndPath("arashi", "textures/gui/widget/" + fileName);
	}

	private ArashiTheme() {
	}
}
