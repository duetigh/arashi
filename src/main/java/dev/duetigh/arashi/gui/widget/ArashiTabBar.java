package dev.duetigh.arashi.gui.widget;

import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import dev.duetigh.arashi.gui.theme.ArashiTheme;

public class ArashiTabBar extends ArashiWidget {
	private final List<String> tabs;
	private final IntConsumer onSelect;
	private int selected;

	public ArashiTabBar(List<String> tabs, int initialSelection, IntConsumer onSelect) {
		this.tabs = tabs;
		this.selected = initialSelection;
		this.onSelect = onSelect;
	}

	public int getSelected() {
		return selected;
	}

	public void setSelected(int index) {
		this.selected = index;
	}

	private int tabWidth() {
		return tabs.isEmpty() ? width : width / tabs.size();
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
		var font = Minecraft.getInstance().font;
		int tabWidth = tabWidth();

		for (int i = 0; i < tabs.size(); i++) {
			int tabX = x + i * tabWidth;
			boolean active = i == selected;
			boolean hovered = mouseX >= tabX && mouseX < tabX + tabWidth && mouseY >= y && mouseY < y + height;

			// Every tab always gets a background fill (not just active/hovered ones) - a 1px gap
			// between adjacent cells is what actually separates them, rather than relying on
			// contrast alone, so two inactive tab labels never visually run together.
			int background = active ? ArashiTheme.PANEL : hovered ? ArashiTheme.CARD_HOVER : ArashiTheme.CARD;
			int cellRight = tabX + tabWidth - (i < tabs.size() - 1 ? 1 : 0);
			ctx.fill(tabX, y, cellRight, y + height, background);

			if (active) {
				ctx.fill(tabX, y + height - 2, cellRight, y + height, ArashiTheme.ACCENT);
			}

			String label = clip(font, tabs.get(i), tabWidth - 6);
			int textWidth = font.width(label);
			int textX = tabX + (tabWidth - textWidth) / 2;
			int textY = y + (height - font.lineHeight) / 2;
			ctx.text(font, label, textX, textY, active ? ArashiTheme.TEXT_PRIMARY : ArashiTheme.TEXT_SECONDARY);
		}
	}

	private static String clip(net.minecraft.client.gui.Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}

		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("...")), false) + "...";
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event) {
		if (!isMouseOver(mouseX, mouseY)) {
			return false;
		}

		int tabWidth = tabWidth();
		int index = (int) ((mouseX - x) / tabWidth);

		if (index >= 0 && index < tabs.size()) {
			selected = index;

			if (onSelect != null) {
				onSelect.accept(index);
			}
		}

		return true;
	}
}
