package dev.duetigh.arashi.gui.widget;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import dev.duetigh.arashi.gui.theme.ArashiTheme;

/** A settings-style row: a text label on the left, a pill switch on the right. */
public class ArashiToggleRow extends ArashiWidget {
	private static final int TOGGLE_WIDTH = 36;
	private static final int TOGGLE_HEIGHT = 16;

	private final String label;
	private final ArashiToggle toggle;

	public ArashiToggleRow(String label, boolean initial, Consumer<Boolean> onChange) {
		this.label = label;
		this.toggle = new ArashiToggle(initial, onChange);
	}

	public boolean getValue() {
		return toggle.getValue();
	}

	@Override
	public void setSize(int width, int height) {
		super.setSize(width, height);
		toggle.setSize(TOGGLE_WIDTH, TOGGLE_HEIGHT);
	}

	@Override
	public void setPosition(int x, int y) {
		super.setPosition(x, y);
		toggle.setPosition(x + width - TOGGLE_WIDTH, y + (height - TOGGLE_HEIGHT) / 2);
	}

	@Override
	public void tick(double mouseX, double mouseY) {
		super.tick(mouseX, mouseY);
		toggle.tick(mouseX, mouseY);
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
		Font font = Minecraft.getInstance().font;
		ctx.text(font, label, x, y + (height - font.lineHeight) / 2, ArashiTheme.TEXT_PRIMARY, false);
		toggle.render(ctx, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event) {
		return toggle.mouseClicked(mouseX, mouseY, event);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, MouseButtonEvent event) {
		return toggle.mouseReleased(mouseX, mouseY, event);
	}
}
