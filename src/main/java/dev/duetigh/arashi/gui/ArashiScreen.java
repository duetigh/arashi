package dev.duetigh.arashi.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.widget.ArashiWidget;

/**
 * Base class for every custom Arashi screen. Deliberately does not use vanilla panel textures,
 * Button, or the addRenderableWidget/children() focus-navigation system - it owns a flat list of
 * ArashiWidgets and dispatches render/input to them itself, so nothing here inherits vanilla
 * look or behavior beyond what Screen requires structurally.
 */
public abstract class ArashiScreen extends Screen {
	protected final Screen parent;
	protected final List<ArashiWidget> widgets = new ArrayList<>();

	protected ArashiScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	protected <T extends ArashiWidget> T track(T widget) {
		widgets.add(widget);
		return widget;
	}

	@Override
	protected final void init() {
		widgets.clear();
		buildWidgets();
	}

	protected abstract void buildWidgets();

	@Override
	public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, ArashiTheme.BACKGROUND);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		extractBackground(ctx, mouseX, mouseY, delta);

		for (ArashiWidget widget : widgets) {
			if (widget.isVisible()) {
				widget.tick(mouseX, mouseY);
				widget.render(ctx, mouseX, mouseY);
			}
		}

		ctx.text(font, getTitle(), 16, 14, ArashiTheme.TEXT_PRIMARY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (int i = widgets.size() - 1; i >= 0; i--) {
			ArashiWidget widget = widgets.get(i);

			if (widget.isVisible() && widget.mouseClicked(event.x(), event.y(), event, doubleClick)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean handled = false;

		for (ArashiWidget widget : widgets) {
			if (widget.isVisible()) {
				handled |= widget.mouseReleased(event.x(), event.y(), event);
			}
		}

		return handled;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		boolean handled = false;

		for (ArashiWidget widget : widgets) {
			if (widget.isVisible()) {
				handled |= widget.mouseDragged(event.x(), event.y(), event, dragX, dragY);
			}
		}

		return handled;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		for (int i = widgets.size() - 1; i >= 0; i--) {
			ArashiWidget widget = widgets.get(i);

			if (widget.isVisible() && widget.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (ArashiWidget widget : widgets) {
			if (widget.isVisible() && widget.keyPressed(event)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		for (ArashiWidget widget : widgets) {
			if (widget.isVisible() && widget.charTyped(event.codepointAsString())) {
				return true;
			}
		}

		return false;
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
