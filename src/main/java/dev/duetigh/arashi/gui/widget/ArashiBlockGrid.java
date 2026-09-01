package dev.duetigh.arashi.gui.widget;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.util.BlockDisplay;

/**
 * Searchable-by-caller icon grid of block ids, used by the ESP tracker picker and the scan
 * whitelist/isolate-seed pickers. The caller owns the search box and any query/view-mode
 * filtering (via {@link #setRows}); this widget only lays out whatever ids it's given, scrolls,
 * and reports clicks back through {@code onClick}.
 */
public class ArashiBlockGrid extends ArashiWidget {
	private static final int CELL_SIZE = 24;
	private static final int ICON_SIZE = 16;

	private final Predicate<Identifier> isSelected;
	private final Predicate<Identifier> isHighlighted;
	private final BiConsumer<Identifier, Boolean> onClick;

	private List<Identifier> blockIds = List.of();
	private int scrollOffset;

	public ArashiBlockGrid(Predicate<Identifier> isSelected, Predicate<Identifier> isHighlighted, BiConsumer<Identifier, Boolean> onClick) {
		this.isSelected = isSelected;
		this.isHighlighted = isHighlighted;
		this.onClick = onClick;
	}

	public void setRows(List<Identifier> blockIds) {
		this.blockIds = blockIds;
		this.scrollOffset = 0;
	}

	private int columns() {
		return Math.max(1, width / CELL_SIZE);
	}

	private int rowCount() {
		int columns = columns();
		return (blockIds.size() + columns - 1) / columns;
	}

	private int maxScroll() {
		return Math.max(0, rowCount() * CELL_SIZE - height);
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
		int columns = columns();
		Identifier hoveredId = null;

		// enableScissor/disableScissor must always pair up - an exception mid-loop would otherwise
		// leave clipping stuck on for every widget rendered afterward this frame.
		ctx.enableScissor(x, y, x + width, y + height);

		try {
			for (int i = 0; i < blockIds.size(); i++) {
				int col = i % columns;
				int row = i / columns;
				int cellX = x + col * CELL_SIZE;
				int cellY = y + row * CELL_SIZE - scrollOffset;

				if (cellY + CELL_SIZE < y || cellY > y + height) {
					continue;
				}

				Identifier blockId = blockIds.get(i);
				boolean hovered = mouseX >= cellX && mouseX < cellX + CELL_SIZE
						&& mouseY >= Math.max(cellY, y) && mouseY < Math.min(cellY + CELL_SIZE, y + height);

				if (hovered) {
					ctx.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, ArashiTheme.CARD_HOVER);
					hoveredId = blockId;
				}

				Block block = BuiltInRegistries.BLOCK.getValue(blockId);

				if (block != null) {
					ctx.item(new ItemStack(block.asItem()), cellX + (CELL_SIZE - ICON_SIZE) / 2, cellY + (CELL_SIZE - ICON_SIZE) / 2);
				}

				if (isSelected.test(blockId)) {
					int color = isHighlighted.test(blockId) ? ArashiTheme.ACCENT : 0xFF55CC55;
					ctx.fill(cellX, cellY, cellX + CELL_SIZE, cellY + 1, color);
					ctx.fill(cellX, cellY + CELL_SIZE - 1, cellX + CELL_SIZE, cellY + CELL_SIZE, color);
					ctx.fill(cellX, cellY, cellX + 1, cellY + CELL_SIZE, color);
					ctx.fill(cellX + CELL_SIZE - 1, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, color);
				}
			}
		} finally {
			ctx.disableScissor();
		}

		if (hoveredId != null) {
			Font font = Minecraft.getInstance().font;
			ctx.setTooltipForNextFrame(font, Component.literal(BlockDisplay.shortName(hoveredId)), (int) mouseX, (int) mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event, boolean doubleClick) {
		if (!isMouseOver(mouseX, mouseY)) {
			return false;
		}

		int columns = columns();
		int localX = (int) (mouseX - x);
		int localY = (int) (mouseY - y) + scrollOffset;
		int col = localX / CELL_SIZE;
		int row = localY / CELL_SIZE;
		int index = row * columns + col;

		if (col >= 0 && col < columns && index >= 0 && index < blockIds.size()) {
			onClick.accept(blockIds.get(index), doubleClick);
		}

		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!isMouseOver(mouseX, mouseY)) {
			return false;
		}

		scrollOffset -= (int) (scrollY * CELL_SIZE);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
		return true;
	}
}
