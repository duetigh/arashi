package dev.duetigh.arashi.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Builds text where each character is styled with a color linearly interpolated
 * between two endpoints, e.g. the blue-to-grey "Arashi" wordmark used in chat messages.
 */
public final class GradientText {
	private static final int BLUE = 0x3B82F6;
	private static final int GREY = 0x9CA3AF;

	private GradientText() {
	}

	public static MutableComponent arashi() {
		return gradient("Arashi", BLUE, GREY);
	}

	public static MutableComponent gradient(String text, int fromRgb, int toRgb) {
		MutableComponent result = Component.empty();
		int length = text.length();

		for (int i = 0; i < length; i++) {
			float t = length == 1 ? 0f : (float) i / (length - 1);
			int rgb = lerpRgb(fromRgb, toRgb, t);
			Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb));
			result.append(Component.literal(String.valueOf(text.charAt(i))).setStyle(style));
		}

		return result;
	}

	private static int lerpRgb(int from, int to, float t) {
		int fr = (from >> 16) & 0xFF;
		int fg = (from >> 8) & 0xFF;
		int fb = from & 0xFF;
		int tr = (to >> 16) & 0xFF;
		int tg = (to >> 8) & 0xFF;
		int tb = to & 0xFF;

		int r = Math.round(fr + (tr - fr) * t);
		int g = Math.round(fg + (tg - fg) * t);
		int b = Math.round(fb + (tb - fb) * t);

		return (r << 16) | (g << 8) | b;
	}
}
