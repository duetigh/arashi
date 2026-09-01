package dev.duetigh.arashi.gui.widget;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import dev.duetigh.arashi.gui.theme.ArashiTheme;

/**
 * A vertically scrollable, clipped region holding other widgets. Children are added with
 * positions relative to the panel's content origin (0,0 = top-left of the scrollable area); the
 * panel repositions them to absolute screen coordinates each frame based on the current scroll
 * offset and clips rendering/input to its own bounds.
 */
public class ArashiScrollPanel extends ArashiWidget {
	private record Entry(ArashiWidget widget, int relativeX, int relativeY) {
	}

	private final List<Entry> entries = new ArrayList<>();
	private int contentHeight;
	private int scrollOffset;

	public void clear() {
		entries.clear();
		contentHeight = 0;
		scrollOffset = 0;
	}

	public void add(ArashiWidget widget, int relativeX, int relativeY) {
		entries.add(new Entry(widget, relativeX, relativeY));
		contentHeight = Math.max(contentHeight, relativeY + widget.getHeight());
	}

	public int getScrollOffset() {
		return scrollOffset;
	}

	public void setScrollOffset(int scrollOffset) {
		this.scrollOffset = scrollOffset;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - height);
	}

	private void layout() {
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));

		for (Entry entry : entries) {
			entry.widget.setPosition(x + entry.relativeX, y + entry.relativeY - scrollOffset);
		}
	}

	@Override
	public void tick(double mouseX, double mouseY) {
		super.tick(mouseX, mouseY);
		layout();

		for (Entry entry : entries) {
			entry.widget.tick(mouseX, mouseY);
		}
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
		layout();
		ctx.enableScissor(x, y, x + width, y + height);

		try {
			for (Entry entry : entries) {
				ArashiWidget widget = entry.widget;

				if (widget.getY() + widget.getHeight() >= y && widget.getY() <= y + height) {
					widget.render(ctx, mouseX, mouseY);
				}
			}
		} finally {
			ctx.disableScissor();
		}

		if (maxScroll() > 0) {
			int trackX = x + width - 3;
			ctx.fill(trackX, y, trackX + 2, y + height, ArashiTheme.PANEL);
			int thumbHeight = Math.max(16, height * height / contentHeight);
			int thumbY = y + (height - thumbHeight) * scrollOffset / maxScroll();
			ctx.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, ArashiTheme.BORDER);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event) {
		return mouseClicked(mouseX, mouseY, event, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event, boolean doubleClick) {
		if (!isMouseOver(mouseX, mouseY)) {
			return false;
		}

		for (int i = entries.size() - 1; i >= 0; i--) {
			if (entries.get(i).widget.mouseClicked(mouseX, mouseY, event, doubleClick)) {
				return true;
			}
		}

		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, MouseButtonEvent event) {
		boolean handled = false;

		for (Entry entry : entries) {
			handled |= entry.widget.mouseReleased(mouseX, mouseY, event);
		}

		return handled;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!isMouseOver(mouseX, mouseY)) {
			return false;
		}

		scrollOffset -= (int) (scrollY * 16);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
		return true;
	}
}
