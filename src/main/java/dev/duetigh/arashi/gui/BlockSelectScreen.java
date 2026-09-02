package dev.duetigh.arashi.gui;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.config.ScanMode;
import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.theme.RoundedRectRenderer;
import dev.duetigh.arashi.gui.widget.ArashiBlockGrid;
import dev.duetigh.arashi.gui.widget.ArashiButton;
import dev.duetigh.arashi.gui.widget.ArashiSlider;
import dev.duetigh.arashi.gui.widget.ArashiTabBar;
import dev.duetigh.arashi.gui.widget.ArashiTextField;
import dev.duetigh.arashi.scan.ScanController;
import dev.duetigh.arashi.scan.ScanStore;
import dev.duetigh.arashi.scanner.BlockScanner;
import dev.duetigh.arashi.util.BlockDisplay;
import dev.duetigh.arashi.waypoint.WaypointEditorState;
import dev.duetigh.arashi.waypoint.WaypointStore;

/**
 * Searchable grid of every registered block's icon, backed by {@link ArashiConfig}. Double-click a
 * block to toggle tracking it; single-click a tracked block to open a side panel for its individual
 * ESP colors. In tracking mode, tracking exactly one block replaces the multi-select toggle behavior.
 */
public final class BlockSelectScreen extends ArashiScreen {
	private static final int SEARCH_WIDTH = 150;
	private static final int VIEW_TAB_WIDTH = 114;
	private static final int MODE_TAB_WIDTH = 114;

	private static final int PANEL_WIDTH = 150;
	private static final int PANEL_PADDING = 8;
	private static final int PANEL_TOP = 48;
	private static final int PANEL_WIDGET_WIDTH = 134;
	private static final int PANEL_WIDGET_HEIGHT = 20;
	private static final int PANEL_SLIDER_WIDTH = 100;
	private static final int PANEL_SWATCH_GAP = 4;
	private static final int PANEL_SWATCH_WIDTH = PANEL_WIDGET_WIDTH - PANEL_SLIDER_WIDTH - PANEL_SWATCH_GAP;
	private static final int PANEL_SPACING = 22;

	private final ArashiConfig config;
	private final BlockScanner scanner;
	private final ScanController scanController;
	private final ScanStore scanStore;
	private final WaypointStore waypointStore;
	private final WaypointEditorState waypointEditorState;
	private final KeyMapping openScannerKey;
	private final KeyMapping toggleEspKey;
	private final KeyMapping toggleScanKey;
	private final KeyMapping openScanBrowserKey;
	private final KeyMapping copyLastCoordsKey;
	private final List<Identifier> allBlockIds;

	private ViewMode viewMode = ViewMode.ALL;
	private String searchQuery = "";
	private Identifier editingBlockId;

	private ArashiBlockGrid grid;

	private int outlineSwatchX;
	private int outlineSwatchY;
	private int fillSwatchX;
	private int fillSwatchY;
	private int panelSwatchHeight;
	private int panelBottom;

	public BlockSelectScreen(ArashiConfig config, BlockScanner scanner, ScanController scanController,
			ScanStore scanStore, WaypointStore waypointStore, WaypointEditorState waypointEditorState,
			KeyMapping openScannerKey, KeyMapping toggleEspKey,
			KeyMapping toggleScanKey, KeyMapping openScanBrowserKey, KeyMapping copyLastCoordsKey) {
		super(Component.literal("Arashi - Block Scanner"), null);
		this.config = config;
		this.scanner = scanner;
		this.scanController = scanController;
		this.scanStore = scanStore;
		this.waypointStore = waypointStore;
		this.waypointEditorState = waypointEditorState;
		this.openScannerKey = openScannerKey;
		this.toggleEspKey = toggleEspKey;
		this.toggleScanKey = toggleScanKey;
		this.openScanBrowserKey = openScanBrowserKey;
		this.copyLastCoordsKey = copyLastCoordsKey;
		this.allBlockIds = BuiltInRegistries.BLOCK.stream()
				.map(BuiltInRegistries.BLOCK::getKey)
				.sorted()
				.toList();
	}

	@Override
	protected void buildWidgets() {
		boolean panelOpen = editingBlockId != null;
		int gridWidth = panelOpen ? this.width - PANEL_WIDTH : this.width;

		int topY = 30;
		int topRowWidth = SEARCH_WIDTH + ArashiTheme.GAP + VIEW_TAB_WIDTH + ArashiTheme.GAP + MODE_TAB_WIDTH;
		// Centered within the grid's available width, not the full screen, so the top row never
		// runs into the color panel occupying the right PANEL_WIDTH pixels while it's open.
		int topRowX = Math.max(ArashiTheme.PADDING, gridWidth / 2 - topRowWidth / 2);

		ArashiTextField searchField = track(new ArashiTextField(searchQuery, query -> {
			searchQuery = query;
			refresh();
		}));
		searchField.setBounds(topRowX, topY, SEARCH_WIDTH, 20);

		ArashiTabBar viewTabs = track(new ArashiTabBar(List.of("All", "Selected", "Unselected"), viewMode.ordinal(), index -> {
			viewMode = ViewMode.values()[index];
			refresh();
		}));
		viewTabs.setBounds(topRowX + SEARCH_WIDTH + ArashiTheme.GAP, topY, VIEW_TAB_WIDTH, 20);

		ArashiTabBar modeTabs = track(new ArashiTabBar(List.of("Multi Scan", "Tracking"), config.scanMode().ordinal(), index -> {
			ScanMode mode = ScanMode.values()[index];
			config.setScanMode(mode);

			if (mode == ScanMode.TRACKING && config.trackedBlockIds().size() > 1) {
				config.setSingleTrackedBlockId(config.trackedBlockIds().iterator().next());
			} else {
				config.save();
			}

			scanner.setTrackedBlockIds(config.trackedBlockIds());
			refresh();
		}));
		modeTabs.setBounds(topRowX + SEARCH_WIDTH + ArashiTheme.GAP + VIEW_TAB_WIDTH + ArashiTheme.GAP, topY, MODE_TAB_WIDTH, 20);

		int gridTop = topY + 20 + ArashiTheme.GAP;
		int bottomY = this.height - ArashiTheme.PADDING - 20;

		grid = track(new ArashiBlockGrid(
				id -> config.trackedBlockIds().contains(id.toString()),
				id -> id.equals(editingBlockId),
				this::onGridClick));
		grid.setBounds(0, gridTop, gridWidth, bottomY - gridTop - ArashiTheme.GAP);

		int buttonWidth = 68;
		int gap = ArashiTheme.GAP;
		int startX = this.width / 2 - (buttonWidth * 5 + gap * 4) / 2;

		ArashiButton settingsButton = track(new ArashiButton("Settings", ArashiButton.Style.SECONDARY, b -> this.minecraft.setScreenAndShow(
				new ArashiSettingsScreen(config, openScannerKey, toggleEspKey, toggleScanKey, openScanBrowserKey, copyLastCoordsKey))));
		settingsButton.setBounds(startX, bottomY, buttonWidth, 20);

		ArashiButton debugButton = track(new ArashiButton(this::debugLabel, ArashiButton.Style.SECONDARY,
				b -> scanner.setDebugMode(!scanner.isDebugMode())));
		debugButton.setBounds(startX + buttonWidth + gap, bottomY, buttonWidth, 20);

		ArashiButton scansButton = track(new ArashiButton("Scans", ArashiButton.Style.SECONDARY,
				b -> this.minecraft.setScreenAndShow(new ScanBrowserScreen(this, scanController, scanStore))));
		scansButton.setBounds(startX + (buttonWidth + gap) * 2, bottomY, buttonWidth, 20);

		ArashiButton waypointsButton = track(new ArashiButton("Waypoints", ArashiButton.Style.SECONDARY,
				b -> this.minecraft.setScreenAndShow(new WaypointManagerScreen(this, config, waypointStore, waypointEditorState))));
		waypointsButton.setBounds(startX + (buttonWidth + gap) * 3, bottomY, buttonWidth, 20);

		ArashiButton doneButton = track(new ArashiButton("Done", ArashiButton.Style.SECONDARY, b -> onClose()));
		doneButton.setBounds(startX + (buttonWidth + gap) * 4, bottomY, buttonWidth, 20);

		if (panelOpen) {
			buildColorPanel(editingBlockId);
		}

		refresh();
	}

	private String debugLabel() {
		return "Debug: " + (scanner.isDebugMode() ? "ON" : "OFF");
	}

	private void refresh() {
		String needle = searchQuery.strip().toLowerCase();

		List<Identifier> filtered = allBlockIds.stream()
				.filter(id -> needle.isEmpty() || id.toString().contains(needle))
				.filter(this::matchesViewMode)
				.toList();

		grid.setRows(filtered);
	}

	private void onGridClick(Identifier blockId, boolean doubleClick) {
		if (doubleClick) {
			toggleTracked(blockId);
		} else if (!blockId.equals(editingBlockId) && config.trackedBlockIds().contains(blockId.toString())) {
			openColorPanel(blockId);
		}
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
		String id = blockId.toString();
		boolean nowTracked;

		if (config.scanMode() == ScanMode.TRACKING) {
			if (config.trackedBlockIds().contains(id)) {
				config.toggle(id);
				nowTracked = false;
			} else {
				config.setSingleTrackedBlockId(id);
				nowTracked = true;
			}
		} else {
			nowTracked = config.toggle(id);
		}

		scanner.setTrackedBlockIds(config.trackedBlockIds());

		if (!nowTracked && blockId.equals(editingBlockId)) {
			closeColorPanel();
		} else {
			refresh();
		}
	}

	/** Adds the close button and outline/fill R/G/B sliders for the block being edited. */
	private void buildColorPanel(Identifier blockId) {
		String id = blockId.toString();
		int x = this.width - PANEL_WIDTH + PANEL_PADDING;
		int contentHeight = PANEL_SPACING + 6 + PANEL_SPACING * 6;
		int top = Math.min(PANEL_TOP, Math.max(34, this.height - 30 - contentHeight));
		int y = top;

		ArashiButton closeButton = track(new ArashiButton("Close", ArashiButton.Style.SECONDARY, b -> closeColorPanel()));
		closeButton.setBounds(x, y, PANEL_WIDGET_WIDTH, PANEL_WIDGET_HEIGHT);
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

			ArashiSlider slider = track(new ArashiSlider(((colorGetter.getAsInt() >> shift) & 0xFF) / 255.0,
					value -> channelLabel + ": " + Math.round(value * 255),
					value -> colorSetter.accept(withChannel(colorGetter.getAsInt(), shift, (int) Math.round(value * 255)))));
			slider.setBounds(x, y, PANEL_SLIDER_WIDTH, PANEL_WIDGET_HEIGHT);
			y += PANEL_SPACING;
		}

		return y;
	}

	private static int withChannel(int rgb, int shift, int value) {
		return (rgb & ~(0xFF << shift)) | ((value & 0xFF) << shift);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		// Runs before the widget loop in extractRenderState(), so the panel fill sits behind the
		// close button/sliders instead of painting over them (they'd otherwise render first, then
		// get covered by this opaque rect if it were drawn after super.extractRenderState()).
		super.extractBackground(ctx, mouseX, mouseY, delta);

		if (editingBlockId != null) {
			int panelX = this.width - PANEL_WIDTH;
			RoundedRectRenderer.fill(ctx, panelX, 30, PANEL_WIDTH, panelBottom + 8 - 30, ArashiTheme.PANEL, ArashiTheme.BACKGROUND);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);

		if (editingBlockId != null) {
			int panelX = this.width - PANEL_WIDTH;
			String name = BlockDisplay.shortName(editingBlockId);
			ctx.text(this.font, name, panelX + PANEL_PADDING, 34, ArashiTheme.TEXT_PRIMARY, false);

			drawSwatch(ctx, outlineSwatchX, outlineSwatchY, config.outlineColorFor(editingBlockId.toString()));
			drawSwatch(ctx, fillSwatchX, fillSwatchY, config.fillColorFor(editingBlockId.toString()));
		}
	}

	private void drawSwatch(GuiGraphicsExtractor ctx, int x, int y, int rgb) {
		ctx.fill(x - 1, y - 1, x + PANEL_SWATCH_WIDTH + 1, y + panelSwatchHeight + 1, ArashiTheme.BORDER);
		ctx.fill(x, y, x + PANEL_SWATCH_WIDTH, y + panelSwatchHeight, 0xFF000000 | rgb);
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
}
