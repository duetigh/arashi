package dev.duetigh.arashi.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.scanner.BlockScanner;
import dev.duetigh.arashi.util.BlockDisplay;

/** Searchable grid of every registered block's icon, backed by {@link ArashiConfig}. Click a block to toggle tracking it. */
public final class BlockSelectScreen extends Screen {
	private static final int CELL_SIZE = 24;
	private static final int ICON_SIZE = 16;

	private final ArashiConfig config;
	private final BlockScanner scanner;
	private final List<Identifier> allBlockIds;

	private EditBox searchBox;
	private BlockGrid grid;
	private Button debugButton;

	public BlockSelectScreen(ArashiConfig config, BlockScanner scanner) {
		super(Component.literal("Arashi - Block Scanner"));
		this.config = config;
		this.scanner = scanner;
		this.allBlockIds = BuiltInRegistries.BLOCK.stream()
				.map(BuiltInRegistries.BLOCK::getKey)
				.sorted()
				.toList();
	}

	@Override
	protected void init() {
		this.searchBox = this.addRenderableWidget(new EditBox(this.font, this.width / 2 - 100, 24, 200, 20, Component.literal("Search")));
		this.searchBox.setResponder(this::refresh);

		this.grid = this.addRenderableOnly(new BlockGrid(this.minecraft, this.width, this.height - 80, 52, CELL_SIZE));
		this.addWidget(this.grid);

		this.debugButton = this.addRenderableWidget(Button.builder(debugLabel(), b -> {
			scanner.setDebugMode(!scanner.isDebugMode());
			b.setMessage(debugLabel());
		}).pos(this.width / 2 - 105, this.height - 26).size(100, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.pos(this.width / 2 + 5, this.height - 26)
				.size(100, 20)
				.build());

		refresh("");
	}

	private Component debugLabel() {
		return Component.literal("Debug: " + (scanner.isDebugMode() ? "ON" : "OFF"));
	}

	private void refresh(String query) {
		this.grid.clear();
		String needle = query.strip().toLowerCase();

		List<Identifier> filtered = allBlockIds.stream()
				.filter(id -> needle.isEmpty() || id.toString().contains(needle))
				.toList();

		int columns = Math.max(1, this.grid.getRowWidth() / CELL_SIZE);

		for (int i = 0; i < filtered.size(); i += columns) {
			this.grid.addEntry(this.grid.new BlockRow(filtered.subList(i, Math.min(i + columns, filtered.size()))));
		}

		this.grid.setScrollAmount(0);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 6, 0xFFFFFFFF, true);
	}

	@Override
	public void onClose() {
		config.save();
		scanner.setTrackedBlockIds(config.trackedBlockIds());
		super.onClose();
	}

	private final class BlockGrid extends AbstractSelectionList<BlockGrid.BlockRow> {
		BlockGrid(Minecraft client, int width, int height, int top, int itemHeight) {
			super(client, width, height, top, itemHeight);
		}

		@Override
		public int addEntry(BlockRow entry) {
			return super.addEntry(entry);
		}

		void clear() {
			this.clearEntries();
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
					boolean tracked = config.trackedBlockIds().contains(blockId.toString());
					boolean cellHovered = hovered && mouseX >= x && mouseX < x + CELL_SIZE
							&& mouseY >= y && mouseY < y + getHeight();

					if (cellHovered) {
						graphics.fill(x, y, x + CELL_SIZE, y + getHeight(), 0x40FFFFFF);
					}

					if (block != null) {
						graphics.item(new ItemStack(block.asItem()), x + (CELL_SIZE - ICON_SIZE) / 2, y + (getHeight() - ICON_SIZE) / 2);
					}

					if (tracked) {
						int color = 0xFF55FF55;
						graphics.fill(x, y, x + CELL_SIZE, y + 1, color);
						graphics.fill(x, y + getHeight() - 1, x + CELL_SIZE, y + getHeight(), color);
						graphics.fill(x, y, x + 1, y + getHeight(), color);
						graphics.fill(x + CELL_SIZE - 1, y, x + CELL_SIZE, y + getHeight(), color);
					}

					if (cellHovered) {
						graphics.setTooltipForNextFrame(BlockSelectScreen.this.font,
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

				config.toggle(blockIds.get(index).toString());
				return true;
			}
		}
	}
}
