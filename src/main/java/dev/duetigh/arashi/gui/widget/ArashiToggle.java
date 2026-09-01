package dev.duetigh.arashi.gui.widget;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.theme.RoundedRectRenderer;
import dev.duetigh.arashi.gui.theme.Transition;

/** A pill-shaped on/off switch. */
public class ArashiToggle extends ArashiWidget {
	private boolean value;
	private final Consumer<Boolean> onChange;
	private final Transition slide;

	public ArashiToggle(boolean initial, Consumer<Boolean> onChange) {
		this.value = initial;
		this.onChange = onChange;
		this.slide = new Transition(120, initial ? 1f : 0f);
	}

	public boolean getValue() {
		return value;
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
		slide.setTarget(value ? 1f : 0f);
		float t = slide.get();

		int off = ArashiTheme.BORDER;
		int on = ArashiTheme.ACCENT;
		int trackColor = ArashiButton.lerpColor(off, on, t);
		RoundedRectRenderer.fill(ctx, x, y, width, height, trackColor, ArashiTheme.BACKGROUND);

		int knobSize = height - 4;
		int travel = width - knobSize - 4;
		int knobX = x + 2 + Math.round(travel * t);
		int knobY = y + 2;
		RoundedRectRenderer.fill(ctx, knobX, knobY, knobSize, knobSize, ArashiTheme.TEXT_PRIMARY, trackColor);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event) {
		boolean handled = super.mouseClicked(mouseX, mouseY, event);

		if (handled) {
			value = !value;

			if (onChange != null) {
				onChange.accept(value);
			}
		}

		return handled;
	}
}
