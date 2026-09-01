package dev.duetigh.arashi.gui.widget;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.theme.RoundedRectRenderer;

/** A clickable card with a square icon, a title, and a subtitle line. */
public class ArashiCard extends ArashiWidget {
	private static final int ICON_SIZE = 48;

	private final Identifier icon;
	private final String title;
	private final String subtitle;
	private final Consumer<ArashiCard> onClick;

	public ArashiCard(Identifier icon, String title, String subtitle, Consumer<ArashiCard> onClick) {
		this.icon = icon;
		this.title = title;
		this.subtitle = subtitle;
		this.onClick = onClick;
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
		int fill = ArashiButton.lerpColor(ArashiTheme.CARD, ArashiTheme.CARD_HOVER, hover.get());
		RoundedRectRenderer.fill(ctx, x, y, width, height, fill, ArashiTheme.BACKGROUND);

		int iconX = x + ArashiTheme.PADDING;
		int iconY = y + (height - ICON_SIZE) / 2;
		ctx.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0f, 0f,
				ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

		var font = Minecraft.getInstance().font;
		int textX = iconX + ICON_SIZE + ArashiTheme.PADDING;
		int textMaxWidth = x + width - textX - ArashiTheme.PADDING;
		int titleY = y + height / 2 - font.lineHeight - 1;
		int subtitleY = y + height / 2 + 1;

		ctx.text(font, clip(font, title, textMaxWidth), textX, titleY, ArashiTheme.TEXT_PRIMARY);
		ctx.text(font, clip(font, subtitle, textMaxWidth), textX, subtitleY, ArashiTheme.TEXT_SECONDARY);
	}

	private static String clip(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}

		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("...")), false) + "...";
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event) {
		boolean handled = super.mouseClicked(mouseX, mouseY, event);

		if (handled && onClick != null) {
			onClick.accept(this);
		}

		return handled;
	}
}
