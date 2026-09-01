package dev.duetigh.arashi.gui.widget;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.theme.RoundedRectRenderer;

/** A horizontal slider whose 0-1 value is mapped to a display label and a config field by the caller. */
public class ArashiSlider extends ArashiWidget {
	private double value;
	private final DoubleFunction<String> labelFactory;
	private final DoubleConsumer onChange;
	private boolean enabled = true;

	public ArashiSlider(double initialValue, DoubleFunction<String> labelFactory, DoubleConsumer onChange) {
		this.value = clamp(initialValue);
		this.labelFactory = labelFactory;
		this.onChange = onChange;
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
		RoundedRectRenderer.fill(ctx, x, y, width, height, ArashiTheme.CARD, ArashiTheme.BACKGROUND);

		int fillWidth = (int) Math.round(width * value);

		if (fillWidth > 0) {
			ctx.fill(x, y, x + fillWidth, y + height, enabled ? ArashiTheme.ACCENT : ArashiTheme.BORDER);
		}

		Font font = Minecraft.getInstance().font;
		String label = labelFactory.apply(value);
		int textX = x + (width - font.width(label)) / 2;
		int textY = y + (height - font.lineHeight) / 2;
		ctx.text(font, label, textX, textY, enabled ? ArashiTheme.TEXT_PRIMARY : ArashiTheme.TEXT_DISABLED);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event) {
		boolean handled = super.mouseClicked(mouseX, mouseY, event);

		if (handled) {
			setValueFromMouse(mouseX);
		}

		return handled;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, MouseButtonEvent event, double dragX, double dragY) {
		if (press.get() <= 0.01f) {
			return false;
		}

		setValueFromMouse(mouseX);
		return true;
	}

	private void setValueFromMouse(double mouseX) {
		value = clamp((mouseX - x) / width);

		if (onChange != null) {
			onChange.accept(value);
		}
	}

	private static double clamp(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}
}
