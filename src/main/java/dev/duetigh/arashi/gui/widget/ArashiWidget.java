package dev.duetigh.arashi.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import dev.duetigh.arashi.gui.theme.Transition;

/**
 * Base type for every widget in Arashi's custom widget library. Deliberately unrelated to
 * vanilla's ClickableWidget/AbstractWidget hierarchy - screens own a flat list of these and
 * dispatch input/render calls to them directly, so nothing here inherits vanilla textures or
 * focus-navigation behavior.
 */
public abstract class ArashiWidget {
	protected int x;
	protected int y;
	protected int width;
	protected int height;
	protected boolean visible = true;

	protected final Transition hover = new Transition(120, 0f);
	protected final Transition press = new Transition(80, 0f);

	public void setPosition(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public void setSize(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public void setBounds(int x, int y, int width, int height) {
		setPosition(x, y);
		setSize(width, height);
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		return visible
				&& mouseX >= x && mouseX < x + width
				&& mouseY >= y && mouseY < y + height;
	}

	/** Called once per frame before render() so hover animation state updates even if unused otherwise. */
	public void tick(double mouseX, double mouseY) {
		hover.setTarget(isMouseOver(mouseX, mouseY) ? 1f : 0f);
	}

	public abstract void render(GuiGraphicsExtractor ctx, double mouseX, double mouseY);

	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event) {
		if (isMouseOver(mouseX, mouseY)) {
			press.setTarget(1f);
			return true;
		}

		return false;
	}

	/** Double-click-aware variant; only widgets that care (e.g. a block grid) need to override this. */
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event, boolean doubleClick) {
		return mouseClicked(mouseX, mouseY, event);
	}

	public boolean mouseReleased(double mouseX, double mouseY, MouseButtonEvent event) {
		boolean wasPressed = press.get() > 0.01f;
		press.setTarget(0f);
		return wasPressed && isMouseOver(mouseX, mouseY);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY, MouseButtonEvent event, double dragX, double dragY) {
		return false;
	}

	public boolean keyPressed(KeyEvent event) {
		return false;
	}

	public boolean charTyped(String character) {
		return false;
	}

	public boolean isFocused() {
		return false;
	}
}
