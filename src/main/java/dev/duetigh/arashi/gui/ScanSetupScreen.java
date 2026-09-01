package dev.duetigh.arashi.gui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.widget.ArashiBlockGrid;
import dev.duetigh.arashi.gui.widget.ArashiButton;
import dev.duetigh.arashi.gui.widget.ArashiSlider;
import dev.duetigh.arashi.gui.widget.ArashiTabBar;
import dev.duetigh.arashi.gui.widget.ArashiTextField;
import dev.duetigh.arashi.scan.CaptureMode;
import dev.duetigh.arashi.scan.CaptureParams;
import dev.duetigh.arashi.scan.ScanController;
import dev.duetigh.arashi.scan.ScanEntry;
import dev.duetigh.arashi.scan.ScanSession;
import dev.duetigh.arashi.scan.ScanStore;

/**
 * Configure and start a scan (Y-range, capture mode), or manage one that's already running. Opening
 * this screen while a scan is active loads its actual live settings (rather than resetting to
 * defaults) so "Manage Running Scan" reflects what's really being captured.
 */
public final class ScanSetupScreen extends ArashiScreen {
	private static final int WIDGET_WIDTH = 150;
	private static final int WIDGET_HEIGHT = 20;

	private final ScanController controller;
	private final ScanStore store;
	private final List<Identifier> allBlockIds;

	private int pendingMinY;
	private int pendingMaxY;
	private CaptureMode pendingMode = CaptureMode.EVERYTHING;
	private final Set<Identifier> whitelistIds = new LinkedHashSet<>();
	private Identifier isolateSeedId;
	private int isolateConnectivity = 6;
	private String pickerQuery = "";
	private String statusMessage = "";

	private ArashiBlockGrid pickerGrid;

	public ScanSetupScreen(Screen parent, ScanController controller, ScanStore store) {
		super(Component.literal("Arashi - Make Scan"), parent);
		this.controller = controller;
		this.store = store;
		this.allBlockIds = BuiltInRegistries.BLOCK.stream()
				.map(BuiltInRegistries.BLOCK::getKey)
				.sorted()
				.toList();

		Minecraft client = Minecraft.getInstance();
		ScanSession active = controller.activeSession();

		if (active != null) {
			pendingMinY = active.minY();
			pendingMaxY = active.maxY();
			pendingMode = active.captureMode();

			if (active.captureParams() instanceof CaptureParams.Whitelist whitelist) {
				for (Block block : whitelist.blocks()) {
					whitelistIds.add(BuiltInRegistries.BLOCK.getKey(block));
				}
			} else if (active.captureParams() instanceof CaptureParams.Isolate isolate) {
				isolateSeedId = BuiltInRegistries.BLOCK.getKey(isolate.seed());
				isolateConnectivity = isolate.connectivity();
			}
		} else if (client.level != null) {
			pendingMinY = client.level.getMinY();
			pendingMaxY = client.level.getMaxY();
		}
	}

	@Override
	protected void buildWidgets() {
		boolean locked = controller.isActive();
		int x = this.width / 2 - WIDGET_WIDTH / 2;
		int y = 24;

		ArashiButton startStopButton = track(new ArashiButton(this::startStopLabel, ArashiButton.Style.PRIMARY, b -> toggleScan()));
		startStopButton.setBounds(x, y, WIDGET_WIDTH, WIDGET_HEIGHT);
		y += 24;

		Minecraft client = Minecraft.getInstance();
		int worldMinY = client.level != null ? client.level.getMinY() : pendingMinY;
		int worldMaxY = client.level != null ? client.level.getMaxY() : pendingMaxY;

		ArashiSlider minYSlider = track(new ArashiSlider(yToSlider(pendingMinY, worldMinY, worldMaxY),
				value -> "Y Min: " + pendingMinY,
				value -> {
					pendingMinY = sliderToY(value, worldMinY, worldMaxY);
					pendingMaxY = Math.max(pendingMaxY, pendingMinY);
				}));
		minYSlider.setBounds(x, y, WIDGET_WIDTH, WIDGET_HEIGHT);
		minYSlider.setEnabled(!locked);
		y += 24;

		ArashiSlider maxYSlider = track(new ArashiSlider(yToSlider(pendingMaxY, worldMinY, worldMaxY),
				value -> "Y Max: " + pendingMaxY,
				value -> {
					pendingMaxY = sliderToY(value, worldMinY, worldMaxY);
					pendingMinY = Math.min(pendingMinY, pendingMaxY);
				}));
		maxYSlider.setBounds(x, y, WIDGET_WIDTH, WIDGET_HEIGHT);
		maxYSlider.setEnabled(!locked);
		y += 24;

		ArashiTabBar modeTabs = track(new ArashiTabBar(List.of("Everything", "Specific Block(s)", "Isolate"), pendingMode.ordinal(), index -> {
			pendingMode = CaptureMode.values()[index];
			this.rebuildWidgets();
		}));
		modeTabs.setBounds(x, y, WIDGET_WIDTH, WIDGET_HEIGHT);
		y += 24;

		if (pendingMode == CaptureMode.ISOLATE) {
			ArashiTabBar connectivityTabs = track(new ArashiTabBar(List.of("6-connectivity", "26-connectivity"),
					isolateConnectivity == 26 ? 1 : 0, index -> isolateConnectivity = index == 1 ? 26 : 6));
			connectivityTabs.setBounds(x, y, WIDGET_WIDTH, WIDGET_HEIGHT);
			y += 24;
		}

		if (pendingMode != CaptureMode.EVERYTHING && !locked) {
			buildPicker(y);
		}

		ArashiButton doneButton = track(new ArashiButton("Done", ArashiButton.Style.SECONDARY, b -> onClose()));
		doneButton.setBounds(x, this.height - 26, WIDGET_WIDTH, WIDGET_HEIGHT);
	}

	private void buildPicker(int top) {
		ArashiTextField searchField = track(new ArashiTextField(pickerQuery, query -> {
			pickerQuery = query;
			refreshPicker();
		}));
		searchField.setBounds(this.width / 2 - WIDGET_WIDTH / 2, top, WIDGET_WIDTH, 20);

		pickerGrid = track(new ArashiBlockGrid(this::isPickerSelected, id -> false, this::onPickerClick));
		pickerGrid.setBounds(0, top + 24, this.width, this.height - top - 24 - 44);

		refreshPicker();
	}

	private boolean isPickerSelected(Identifier id) {
		return pendingMode == CaptureMode.WHITELIST ? whitelistIds.contains(id) : id.equals(isolateSeedId);
	}

	private void onPickerClick(Identifier id, boolean doubleClick) {
		if (pendingMode == CaptureMode.WHITELIST) {
			if (!whitelistIds.remove(id)) {
				whitelistIds.add(id);
			}
		} else {
			isolateSeedId = id;
		}
	}

	private void refreshPicker() {
		String needle = pickerQuery.strip().toLowerCase();
		List<Identifier> filtered = allBlockIds.stream()
				.filter(id -> needle.isEmpty() || id.toString().contains(needle))
				.toList();
		pickerGrid.setRows(filtered);
	}

	private void toggleScan() {
		Minecraft client = Minecraft.getInstance();

		if (controller.isActive()) {
			ScanEntry entry = controller.stop();
			statusMessage = entry != null ? "Saved \"" + entry.name() + "\" (" + entry.chunkCount() + " chunks)." : "Scan discarded (no chunks captured).";
			this.rebuildWidgets();
		} else if (client.level != null) {
			CaptureParams params = switch (pendingMode) {
				case EVERYTHING -> CaptureParams.EVERYTHING;
				case WHITELIST -> new CaptureParams.Whitelist(toBlocks(whitelistIds));
				case ISOLATE -> new CaptureParams.Isolate(
						isolateSeedId != null ? BuiltInRegistries.BLOCK.getValue(isolateSeedId) : Blocks.AIR,
						isolateConnectivity);
			};

			controller.start(client.level, pendingMinY, pendingMaxY, pendingMode, params);
			statusMessage = "";
			this.rebuildWidgets();
		} else {
			statusMessage = "You must be in a world to start a scan.";
		}
	}

	private static Set<Block> toBlocks(Set<Identifier> ids) {
		Set<Block> blocks = new LinkedHashSet<>();

		for (Identifier id : ids) {
			blocks.add(BuiltInRegistries.BLOCK.getValue(id));
		}

		return blocks;
	}

	private String startStopLabel() {
		return controller.isActive() ? "Stop Scan (saves)" : "Start Scan";
	}

	private static double yToSlider(int y, int minY, int maxY) {
		return maxY > minY ? (double) (y - minY) / (maxY - minY) : 0;
	}

	private static int sliderToY(double value, int minY, int maxY) {
		return minY + (int) Math.round(value * (maxY - minY));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);

		if (controller.isActive()) {
			String status = "Scanning... " + controller.activeChunkCount() + " chunks captured";
			ctx.text(this.font, Component.literal(status), this.width / 2 - this.font.width(status) / 2, this.height - 46, 0xFF55FF55, true);
		} else if (!statusMessage.isEmpty()) {
			ctx.text(this.font, Component.literal(statusMessage), this.width / 2 - this.font.width(statusMessage) / 2, this.height - 46, ArashiTheme.TEXT_SECONDARY, true);
		}
	}
}
