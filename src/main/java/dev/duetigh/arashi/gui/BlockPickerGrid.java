package dev.duetigh.arashi.gui;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import dev.duetigh.arashi.util.BlockDisplay;

/**
 * Searchable-by-caller icon grid of block ids, shared by {@link BlockSelectScreen}'s ESP tracker
 * picker and {@link ScanSetupScreen}'s whitelist/isolate-seed pickers. The caller owns the search
 * box and any query/view-mode filtering (via {@link #setRows}); this widget only lays out whatever
 * ids it's given and reports clicks back through {@code onClick}.
 */
final class BlockPickerGrid extends AbstractSelectionList<BlockPickerGrid.BlockRow> {
	static final int CELL_SIZE = 24;
	private static final int ICON_SIZE = 16;

	private final Predicate<Identifier> isSelected;
	private final Predicate<Identifier> isHighlighted;
	private final BiConsumer<Identifier, Boolean> onClick;

	BlockPickerGrid(Minecraft client, int width, int height, int top,
			Predicate<Identifier> isSelected, Predicate<Identifier> isHighlighted, BiConsumer<Identifier, Boolean> onClick) {
		super(client, width, height, top, CELL_SIZE);
		this.isSelected = isSelected;
		this.isHighlighted = isHighlighted;
		this.onClick = onClick;
	}

	void clear() {
		this.clearEntries();
	}

	/** Replaces the grid's contents, wrapping {@code blockIds} into rows sized to the current width. */
	void setRows(List<Identifier> blockIds) {
		clear();
		int columns = Math.max(1, getRowWidth() / CELL_SIZE);

		for (int i = 0; i < blockIds.size(); i += columns) {
			addEntry(new BlockRow(blockIds.subList(i, Math.min(i + columns, blockIds.size()))));
		}

		setScrollAmount(0);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}

	final class BlockRow extends AbstractSelectionList.Entry<BlockRow> {
		private final List<Identifier> blockIds;

		BlockRow(List<Identifier> blockIds) {
			this.blockIds = blockIds;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			for (int i = 0; i < blockIds.size(); i++) {
				Identifier blockId = blockIds.get(i);
				Block block = BuiltInRegistries.BLOCK.getValue(blockId);
				int x = getX() + i * CELL_SIZE;
				int y = getY();
				boolean selected = isSelected.test(blockId);
				boolean highlighted = isHighlighted.test(blockId);
				boolean cellHovered = hovered && mouseX >= x && mouseX < x + CELL_SIZE
						&& mouseY >= y && mouseY < y + getHeight();

				if (cellHovered) {
					graphics.fill(x, y, x + CELL_SIZE, y + getHeight(), 0x40FFFFFF);
				}

				if (block != null) {
					graphics.item(new ItemStack(block.asItem()), x + (CELL_SIZE - ICON_SIZE) / 2, y + (getHeight() - ICON_SIZE) / 2);
				}

				if (selected) {
					int color = highlighted ? 0xFFFFFF55 : 0xFF55FF55;
					graphics.fill(x, y, x + CELL_SIZE, y + 1, color);
					graphics.fill(x, y + getHeight() - 1, x + CELL_SIZE, y + getHeight(), color);
					graphics.fill(x, y, x + 1, y + getHeight(), color);
					graphics.fill(x + CELL_SIZE - 1, y, x + CELL_SIZE, y + getHeight(), color);
				}

				if (cellHovered) {
					graphics.setTooltipForNextFrame(BlockPickerGrid.this.minecraft.font,
							Component.literal(BlockDisplay.shortName(blockId)), mouseX, mouseY);
				}
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			int index = (int) ((event.x() - getX()) / CELL_SIZE);

			if (index < 0 || index >= blockIds.size()) {
				return false;
			}

			onClick.accept(blockIds.get(index), doubleClick);
			return true;
		}
	}
}
