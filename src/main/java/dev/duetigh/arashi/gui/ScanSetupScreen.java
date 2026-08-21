package dev.duetigh.arashi.gui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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
public final class ScanSetupScreen extends Screen {
	private static final int WIDGET_WIDTH = 200;
	private static final int WIDGET_HEIGHT = 20;

	private final Screen parent;
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

	private EditBox pickerSearchBox;
	private BlockPickerGrid pickerGrid;

	public ScanSetupScreen(Screen parent, ScanController controller, ScanStore store) {
		super(Component.literal("Arashi - Make Scan"));
		this.parent = parent;
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
	protected void init() {
		boolean locked = controller.isActive();
		int x = this.width / 2 - WIDGET_WIDTH / 2;
		int y = 24;

		this.addRenderableWidget(Button.builder(startStopLabel(), b -> toggleScan())
				.pos(x, y)
				.size(WIDGET_WIDTH, WIDGET_HEIGHT)
				.build());
		y += 24;

		Minecraft client = Minecraft.getInstance();
		int worldMinY = client.level != null ? client.level.getMinY() : pendingMinY;
		int worldMaxY = client.level != null ? client.level.getMaxY() : pendingMaxY;

		AbstractWidget minYSlider = this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
				yToSlider(pendingMinY, worldMinY, worldMaxY),
				value -> Component.literal("Y Min: " + pendingMinY),
				value -> {
					pendingMinY = sliderToY(value, worldMinY, worldMaxY);
					pendingMaxY = Math.max(pendingMaxY, pendingMinY);
				}));
		minYSlider.active = !locked;
		y += 24;

		AbstractWidget maxYSlider = this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT,
				yToSlider(pendingMaxY, worldMinY, worldMaxY),
				value -> Component.literal("Y Max: " + pendingMaxY),
				value -> {
					pendingMaxY = sliderToY(value, worldMinY, worldMaxY);
					pendingMinY = Math.min(pendingMinY, pendingMaxY);
				}));
		maxYSlider.active = !locked;
		y += 24;

		CycleButton<CaptureMode> modeButton = this.addRenderableWidget(CycleButton.builder(
						(CaptureMode mode) -> Component.literal("Mode: " + modeLabel(mode)), pendingMode)
				.withValues(CaptureMode.values())
				.displayOnlyValue()
				.create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), (button, mode) -> {
					pendingMode = mode;
					this.rebuildWidgets();
				}));
		modeButton.active = !locked;
		y += 24;

		if (pendingMode == CaptureMode.ISOLATE) {
			CycleButton<Integer> connectivityButton = this.addRenderableWidget(CycleButton.builder(
							(Integer c) -> Component.literal(c + "-connectivity"), isolateConnectivity)
					.withValues(6, 26)
					.create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), (button, c) -> isolateConnectivity = c));
			connectivityButton.active = !locked;
			y += 24;
		}

		if (pendingMode != CaptureMode.EVERYTHING && !locked) {
			buildPicker(y);
		}

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.pos(x, this.height - 26)
				.size(WIDGET_WIDTH, WIDGET_HEIGHT)
				.build());
	}

	private void buildPicker(int top) {
		this.pickerSearchBox = this.addRenderableWidget(new EditBox(this.font, this.width / 2 - WIDGET_WIDTH / 2, top, WIDGET_WIDTH, 20, Component.literal("Search")));
		this.pickerSearchBox.setValue(pickerQuery);
		this.pickerSearchBox.setResponder(query -> {
			pickerQuery = query;
			refreshPicker();
		});

		this.pickerGrid = this.addRenderableOnly(new BlockPickerGrid(this.minecraft, this.width, this.height - top - 44, top + 24,
				this::isPickerSelected, id -> false, this::onPickerClick));
		this.addWidget(this.pickerGrid);
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

	private Component startStopLabel() {
		return Component.literal(controller.isActive() ? "Stop Scan (saves)" : "Start Scan");
	}

	private static String modeLabel(CaptureMode mode) {
		return switch (mode) {
			case EVERYTHING -> "Everything";
			case WHITELIST -> "Specific Block(s)";
			case ISOLATE -> "Isolate";
		};
	}

	private static double yToSlider(int y, int minY, int maxY) {
		return maxY > minY ? (double) (y - minY) / (maxY - minY) : 0;
	}

	private static int sliderToY(double value, int minY, int maxY) {
		return minY + (int) Math.round(value * (maxY - minY));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 6, 0xFFFFFFFF, true);

		if (controller.isActive()) {
			String status = "Scanning... " + controller.activeChunkCount() + " chunks captured";
			graphics.text(this.font, Component.literal(status), this.width / 2 - this.font.width(status) / 2, this.height - 46, 0xFF55FF55, true);
		} else if (!statusMessage.isEmpty()) {
			graphics.text(this.font, Component.literal(statusMessage), this.width / 2 - this.font.width(statusMessage) / 2, this.height - 46, 0xFFAAAAAA, true);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
