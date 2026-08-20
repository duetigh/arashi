package dev.duetigh.arashi.gui;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
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

/**
 * Searchable grid of every registered block's icon, backed by {@link ArashiConfig}. Double-click a
 * block to toggle tracking it; single-click a tracked block to open a side panel for its individual
 * ESP colors. The view can be filtered to show all blocks, only selected ones, or only unselected ones.
 */
public final class BlockSelectScreen extends Screen {
	private static final int CELL_SIZE = 24;
	private static final int ICON_SIZE = 16;
	private static final int SEARCH_WIDTH = 200;
	private static final int TOP_ROW_GAP = 8;
	private static final int VIEW_BUTTON_WIDTH = 110;

	private static final int PANEL_WIDTH = 176;
	private static final int PANEL_PADDING = 8;
	private static final int PANEL_TOP = 48;
	private static final int PANEL_WIDGET_WIDTH = 160;
	private static final int PANEL_WIDGET_HEIGHT = 20;
	private static final int PANEL_SLIDER_WIDTH = 120;
	private static final int PANEL_SWATCH_GAP = 4;
	private static final int PANEL_SWATCH_WIDTH = PANEL_WIDGET_WIDTH - PANEL_SLIDER_WIDTH - PANEL_SWATCH_GAP;
	private static final int PANEL_SPACING = 22;

	private final ArashiConfig config;
	private final BlockScanner scanner;
	private final dev.duetigh.arashi.scan.ScanController scanController;
	private final dev.duetigh.arashi.scan.ScanStore scanStore;
	private final KeyMapping openScannerKey;
	private final KeyMapping toggleEspKey;
	private final KeyMapping toggleScanKey;
	private final KeyMapping openScanBrowserKey;
	private final List<Identifier> allBlockIds;

	private ViewMode viewMode = ViewMode.ALL;
	private String searchQuery = "";
	private Identifier editingBlockId;

	private EditBox searchBox;
	private BlockGrid grid;
	private Button debugButton;

	private int outlineSwatchX;
	private int outlineSwatchY;
	private int fillSwatchX;
	private int fillSwatchY;
	private int panelSwatchHeight;
	private int panelBottom;

	public BlockSelectScreen(ArashiConfig config, BlockScanner scanner, dev.duetigh.arashi.scan.ScanController scanController,
			dev.duetigh.arashi.scan.ScanStore scanStore, KeyMapping openScannerKey, KeyMapping toggleEspKey,
			KeyMapping toggleScanKey, KeyMapping openScanBrowserKey) {
		super(Component.literal("Arashi - Block Scanner"));
		this.config = config;
		this.scanner = scanner;
		this.scanController = scanController;
		this.scanStore = scanStore;
		this.openScannerKey = openScannerKey;
		this.toggleEspKey = toggleEspKey;
		this.toggleScanKey = toggleScanKey;
		this.openScanBrowserKey = openScanBrowserKey;
		this.allBlockIds = BuiltInRegistries.BLOCK.stream()
				.map(BuiltInRegistries.BLOCK::getKey)
				.sorted()
				.toList();
	}

	@Override
	protected void init() {
		boolean panelOpen = editingBlockId != null;
		int gridWidth = panelOpen ? this.width - PANEL_WIDTH : this.width;

		int topRowWidth = SEARCH_WIDTH + TOP_ROW_GAP + VIEW_BUTTON_WIDTH;
		int topRowX = this.width / 2 - topRowWidth / 2;

		this.searchBox = this.addRenderableWidget(new EditBox(this.font, topRowX, 24, SEARCH_WIDTH, 20, Component.literal("Search")));
		this.searchBox.setValue(searchQuery);
		this.searchBox.setResponder(query -> {
			searchQuery = query;
			refresh(query);
		});

		this.addRenderableWidget(CycleButton.builder((ViewMode mode) -> Component.literal(viewModeLabel(mode)), viewMode)
				.withValues(ViewMode.values())
				.displayOnlyValue()
				.create(topRowX + SEARCH_WIDTH + TOP_ROW_GAP, 24, VIEW_BUTTON_WIDTH, 20, Component.empty(),
						(button, mode) -> {
							viewMode = mode;
							refresh(searchQuery);
						}));

		this.grid = this.addRenderableOnly(new BlockGrid(this.minecraft, gridWidth, this.height - 80, 52, CELL_SIZE));
		this.addWidget(this.grid);

		int buttonWidth = 90;
		int gap = 5;
		int startX = this.width / 2 - (buttonWidth * 4 + gap * 3) / 2;

		this.addRenderableWidget(Button.builder(Component.literal("Settings"), b -> this.minecraft.setScreen(
				new ArashiSettingsScreen(config, openScannerKey, toggleEspKey, toggleScanKey, openScanBrowserKey)))
				.pos(startX, this.height - 26)
				.size(buttonWidth, 20)
				.build());

		this.debugButton = this.addRenderableWidget(Button.builder(debugLabel(), b -> {
			scanner.setDebugMode(!scanner.isDebugMode());
			b.setMessage(debugLabel());
		}).pos(startX + buttonWidth + gap, this.height - 26).size(buttonWidth, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Scans"), b -> this.minecraft.setScreen(new ScanBrowserScreen(this, scanController, scanStore)))
				.pos(startX + (buttonWidth + gap) * 2, this.height - 26)
				.size(buttonWidth, 20)
				.build());

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.pos(startX + (buttonWidth + gap) * 3, this.height - 26)
				.size(buttonWidth, 20)
				.build());

		if (panelOpen) {
			buildColorPanel(editingBlockId);
		}

		refresh(searchQuery);
	}

	private Component debugLabel() {
		return Component.literal("Debug: " + (scanner.isDebugMode() ? "ON" : "OFF"));
	}

	private static String viewModeLabel(ViewMode mode) {
		return switch (mode) {
			case ALL -> "Showing: All";
			case SELECTED -> "Showing: Selected";
			case UNSELECTED -> "Showing: Unselected";
		};
	}

	private void refresh(String query) {
		this.grid.clear();
		String needle = query.strip().toLowerCase();

		List<Identifier> filtered = allBlockIds.stream()
				.filter(id -> needle.isEmpty() || id.toString().contains(needle))
				.filter(this::matchesViewMode)
				.toList();

		int columns = Math.max(1, this.grid.getRowWidth() / CELL_SIZE);

		for (int i = 0; i < filtered.size(); i += columns) {
			this.grid.addEntry(this.grid.new BlockRow(filtered.subList(i, Math.min(i + columns, filtered.size()))));
		}

		this.grid.setScrollAmount(0);
	}

	private boolean matchesViewMode(Identifier id) {
		boolean tracked = config.trackedBlockIds().contains(id.toString());

		return switch (viewMode) {
			case ALL -> true;
			case SELECTED -> tracked;
			case UNSELECTED -> !tracked;
		};
	}

	private void openColorPanel(Identifier blockId) {
		editingBlockId = blockId;
		this.rebuildWidgets();
	}

	private void closeColorPanel() {
		editingBlockId = null;
		this.rebuildWidgets();
	}

	private void toggleTracked(Identifier blockId) {
		boolean nowTracked = config.toggle(blockId.toString());

		if (!nowTracked && blockId.equals(editingBlockId)) {
			closeColorPanel();
		} else {
			refresh(searchQuery);
		}
	}

	/** Adds the close button and outline/fill R/G/B sliders for the block being edited. */
	private void buildColorPanel(Identifier blockId) {
		String id = blockId.toString();
		int x = this.width - PANEL_WIDTH + PANEL_PADDING;
		int y = PANEL_TOP;

		this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> closeColorPanel())
				.pos(x, y)
				.size(PANEL_WIDGET_WIDTH, PANEL_WIDGET_HEIGHT)
				.build());
		y += PANEL_SPACING + 6;

		outlineSwatchX = x + PANEL_SLIDER_WIDTH + PANEL_SWATCH_GAP;
		outlineSwatchY = y;
		y = addColorSliders(x, y, "Outline", () -> config.outlineColorFor(id), rgb -> config.setOutlineColorFor(id, rgb));

		fillSwatchX = x + PANEL_SLIDER_WIDTH + PANEL_SWATCH_GAP;
		fillSwatchY = y;
		y = addColorSliders(x, y, "Fill", () -> config.fillColorFor(id), rgb -> config.setFillColorFor(id, rgb));

		panelSwatchHeight = PANEL_SPACING * 3 - PANEL_SWATCH_GAP;
		panelBottom = y;
	}

	/** Adds R/G/B sliders (0-255) for a packed 0xRRGGBB color, returning the y position after them. */
	private int addColorSliders(int x, int y, String label, IntSupplier colorGetter, IntConsumer colorSetter) {
		int[] shifts = {16, 8, 0};
		String[] channelNames = {"R", "G", "B"};

		for (int i = 0; i < shifts.length; i++) {
			int shift = shifts[i];
			String channelLabel = label + " " + channelNames[i];

			this.addRenderableWidget(new ValueSlider(x, y, PANEL_SLIDER_WIDTH, PANEL_WIDGET_HEIGHT, ((colorGetter.getAsInt() >> shift) & 0xFF) / 255.0,
					value -> Component.literal(channelLabel + ": " + Math.round(value * 255)),
					value -> colorSetter.accept(withChannel(colorGetter.getAsInt(), shift, (int) Math.round(value * 255)))));
			y += PANEL_SPACING;
		}

		return y;
	}

	private static int withChannel(int rgb, int shift, int value) {
		return (rgb & ~(0xFF << shift)) | ((value & 0xFF) << shift);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 6, 0xFFFFFFFF, true);

		if (editingBlockId != null) {
			int panelX = this.width - PANEL_WIDTH;
			graphics.fill(panelX, 30, this.width, panelBottom + 8, 0xC0000000);

			Component name = Component.literal(BlockDisplay.shortName(editingBlockId));
			graphics.text(this.font, name, panelX + PANEL_PADDING, 34, 0xFFFFFFFF, true);

			drawSwatch(graphics, outlineSwatchX, outlineSwatchY, config.outlineColorFor(editingBlockId.toString()));
			drawSwatch(graphics, fillSwatchX, fillSwatchY, config.fillColorFor(editingBlockId.toString()));
		}
	}

	private void drawSwatch(GuiGraphicsExtractor graphics, int x, int y, int rgb) {
		graphics.fill(x - 1, y - 1, x + PANEL_SWATCH_WIDTH + 1, y + panelSwatchHeight + 1, 0xFFFFFFFF);
		graphics.fill(x, y, x + PANEL_SWATCH_WIDTH, y + panelSwatchHeight, 0xFF000000 | rgb);
	}

	@Override
	public void onClose() {
		config.save();
		scanner.setTrackedBlockIds(config.trackedBlockIds());
		super.onClose();
	}

	private enum ViewMode {
		ALL,
		SELECTED,
		UNSELECTED
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
					boolean editing = blockId.equals(editingBlockId);
					boolean cellHovered = hovered && mouseX >= x && mouseX < x + CELL_SIZE
							&& mouseY >= y && mouseY < y + getHeight();

					if (cellHovered) {
						graphics.fill(x, y, x + CELL_SIZE, y + getHeight(), 0x40FFFFFF);
					}

					if (block != null) {
						graphics.item(new ItemStack(block.asItem()), x + (CELL_SIZE - ICON_SIZE) / 2, y + (getHeight() - ICON_SIZE) / 2);
					}

					if (tracked) {
						int color = editing ? 0xFFFFFF55 : 0xFF55FF55;
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

				Identifier blockId = blockIds.get(index);

				if (doubleClick) {
					toggleTracked(blockId);
				} else if (!blockId.equals(editingBlockId) && config.trackedBlockIds().contains(blockId.toString())) {
					openColorPanel(blockId);
				}

				return true;
			}
		}
	}
}
