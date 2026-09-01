package dev.duetigh.arashi.gui.theme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws the rounded-corner surfaces every Arashi widget uses instead of vanilla panel textures.
 * GuiGraphicsExtractor has no native rounded-rect primitive, so this fills a plain square first
 * and then stamps a small pre-baked "cut" mask over each corner, tinted to the color sitting
 * behind the widget, to punch the corner tip back to that background color.
 */
public final class RoundedRectRenderer {

	private RoundedRectRenderer() {
	}

	public static void fill(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
			int fillColor, int backgroundColor) {
		int r = ArashiTheme.CORNER_RADIUS;
		ctx.fill(x, y, x + width, y + height, fillColor);

		if (width < r * 2 || height < r * 2) {
			return;
		}

		stampCorner(ctx, ArashiTheme.CORNER_TOP_LEFT, x, y, backgroundColor);
		stampCorner(ctx, ArashiTheme.CORNER_TOP_RIGHT, x + width - r, y, backgroundColor);
		stampCorner(ctx, ArashiTheme.CORNER_BOTTOM_LEFT, x, y + height - r, backgroundColor);
		stampCorner(ctx, ArashiTheme.CORNER_BOTTOM_RIGHT, x + width - r, y + height - r, backgroundColor);
	}

	private static void stampCorner(GuiGraphicsExtractor ctx, Identifier texture, int x, int y, int tint) {
		int r = ArashiTheme.CORNER_RADIUS;
		ctx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, r, r, r, r, tint);
	}
}
