package dev.duetigh.arashi.gui.widget;

import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.theme.RoundedRectRenderer;

/** A minimal single-line text input: click to focus, type to edit, backspace to delete. */
public class ArashiTextField extends ArashiWidget {
	private final StringBuilder value = new StringBuilder();
	private final Consumer<String> onChange;
	private boolean focused;

	public ArashiTextField(String initial, Consumer<String> onChange) {
		this.value.append(initial);
		this.onChange = onChange;
	}

	public String getValue() {
		return value.toString();
	}

	public void setFocused(boolean focused) {
		this.focused = focused;
	}

	@Override
	public boolean isFocused() {
		return focused;
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
		int fill = focused ? ArashiTheme.CARD_HOVER : ArashiTheme.CARD;
		RoundedRectRenderer.fill(ctx, x, y, width, height, fill, ArashiTheme.BACKGROUND);

		Font font = Minecraft.getInstance().font;
		String display = value.toString();
		int textY = y + (height - font.lineHeight) / 2;

		ctx.enableScissor(x + ArashiTheme.PADDING, y, x + width - ArashiTheme.PADDING, y + height);
		ctx.text(font, display, x + ArashiTheme.PADDING, textY, ArashiTheme.TEXT_PRIMARY);

		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cursorX = x + ArashiTheme.PADDING + font.width(display);
			ctx.fill(cursorX, textY - 1, cursorX + 1, textY + font.lineHeight, ArashiTheme.TEXT_PRIMARY);
		}

		ctx.disableScissor();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event) {
		focused = isMouseOver(mouseX, mouseY);
		return focused;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}

		if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !value.isEmpty()) {
			value.deleteCharAt(value.length() - 1);
			fireChange();
		}

		return true;
	}

	@Override
	public boolean charTyped(String character) {
		if (!focused) {
			return false;
		}

		value.append(character);
		fireChange();
		return true;
	}

	private void fireChange() {
		if (onChange != null) {
			onChange.accept(value.toString());
		}
	}
}
