package dev.duetigh.arashi.gui.widget;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.theme.RoundedRectRenderer;

public class ArashiButton extends ArashiWidget {
	public enum Style { PRIMARY, SECONDARY }

	private final Supplier<String> label;
	private final Style style;
	private final Consumer<ArashiButton> onClick;
	private boolean enabled = true;

	public ArashiButton(String label, Style style, Consumer<ArashiButton> onClick) {
		this(() -> label, style, onClick);
	}

	/** For a label that changes at render time (e.g. a live keybind name or an ON/OFF toggle). */
	public ArashiButton(Supplier<String> label, Style style, Consumer<ArashiButton> onClick) {
		this.label = label;
		this.style = style;
		this.onClick = onClick;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return enabled && super.isMouseOver(mouseX, mouseY);
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
		float h = hover.get();
		float p = press.get();

		int base = style == Style.PRIMARY ? ArashiTheme.ACCENT : ArashiTheme.CARD;
		int hoverColor = style == Style.PRIMARY ? ArashiTheme.ACCENT_HOVER : ArashiTheme.CARD_HOVER;
		int pressedColor = style == Style.PRIMARY ? ArashiTheme.ACCENT_PRESSED : ArashiTheme.BORDER;

		int fill = lerpColor(lerpColor(base, hoverColor, h), pressedColor, p);

		if (!enabled) {
			fill = ArashiTheme.PANEL;
		}

		RoundedRectRenderer.fill(ctx, x, y, width, height, fill, ArashiTheme.BACKGROUND);

		int textColor = style == Style.PRIMARY ? ArashiTheme.ACCENT_TEXT : ArashiTheme.TEXT_PRIMARY;

		if (!enabled) {
			textColor = ArashiTheme.TEXT_DISABLED;
		}

		var font = Minecraft.getInstance().font;
		String text = label.get();
		int textWidth = font.width(text);
		int textX = x + (width - textWidth) / 2;
		int textY = y + (height - font.lineHeight) / 2;
		ctx.text(font, text, textX, textY, textColor);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event) {
		boolean handled = super.mouseClicked(mouseX, mouseY, event);

		if (handled && onClick != null) {
			onClick.accept(this);
		}

		return handled;
	}

	static int lerpColor(int from, int to, float t) {
		if (t <= 0f) {
			return from;
		}

		if (t >= 1f) {
			return to;
		}

		int fa = (from >>> 24) & 0xFF, fr = (from >>> 16) & 0xFF, fg = (from >>> 8) & 0xFF, fb = from & 0xFF;
		int ta = (to >>> 24) & 0xFF, tr = (to >>> 16) & 0xFF, tg = (to >>> 8) & 0xFF, tb = to & 0xFF;
		int a = (int) (fa + (ta - fa) * t);
		int r = (int) (fr + (tr - fr) * t);
		int g = (int) (fg + (tg - fg) * t);
		int b = (int) (fb + (tb - fb) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
