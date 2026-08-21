package dev.duetigh.arashi.gui;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.duetigh.arashi.scan.ScanController;
import dev.duetigh.arashi.scan.ScanEntry;
import dev.duetigh.arashi.scan.ScanStore;

/**
 * Browse, rename, delete, or export previously saved scans. While a scan is running, this screen
 * is locked to a banner pointing at {@link ScanSetupScreen} - the list/export/delete actions below
 * would otherwise operate on stale data (or race a scan that's still writing to disk).
 */
public final class ScanBrowserScreen extends Screen {
	private static final int ROW_HEIGHT = 36;
	private static final int ACTION_ROW_Y_OFFSET = 24;
	private static final int ACTION_WIDTH = 50;
	private static final int ACTION_COUNT = 4;
	private static final long SIZE_WARNING_BYTES = 300_000;
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
			.withZone(ZoneId.systemDefault());

	private final Screen parent;
	private final ScanController controller;
	private final ScanStore store;

	private ScanList list;
	private String renamingId;
	private EditBox renameBox;
	private String statusMessage = "";

	public ScanBrowserScreen(Screen parent, ScanController controller, ScanStore store) {
		super(Component.literal("Arashi - Scans"));
		this.parent = parent;
		this.controller = controller;
		this.store = store;
	}

	@Override
	protected void init() {
		if (controller.isActive()) {
			initActiveScanBanner();
		} else {
			initScanList();
		}
	}

	/** Locked-out view while a scan is running: nothing here can touch the scan list until it's stopped. */
	private void initActiveScanBanner() {
		this.addRenderableWidget(Button.builder(Component.literal("Manage Running Scan"),
						b -> this.minecraft.setScreen(new ScanSetupScreen(this, controller, store)))
				.pos(this.width / 2 - 100, this.height / 2 - 30)
				.size(200, 20)
				.build());

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.pos(this.width / 2 - 50, this.height - 26)
				.size(100, 20)
				.build());
	}

	private void initScanList() {
		this.addRenderableWidget(Button.builder(Component.literal("Make Scan"),
						b -> this.minecraft.setScreen(new ScanSetupScreen(this, controller, store)))
				.pos(this.width / 2 - 100, 24)
				.size(200, 20)
				.build());

		int listTop = 50;

		if (renamingId != null) {
			listTop = 78;
			renameBox = this.addRenderableWidget(new EditBox(this.font, this.width / 2 - 100, 50, 160, 20, Component.literal("Name")));
			ScanEntry entry = findEntry(renamingId);
			renameBox.setValue(entry != null ? entry.name() : "");
			renameBox.setFocused(true);

			this.addRenderableWidget(Button.builder(Component.literal("OK"), b -> confirmRename())
					.pos(this.width / 2 + 64, 50)
					.size(36, 20)
					.build());
		}

		this.list = this.addRenderableOnly(new ScanList(this.minecraft, this.width, this.height - listTop - 32, listTop, ROW_HEIGHT));
		this.addWidget(this.list);
		refreshList();

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.pos(this.width / 2 - 50, this.height - 26)
				.size(100, 20)
				.build());
	}

	private void refreshList() {
		list.clear();

		for (ScanEntry entry : store.list()) {
			list.addEntry(list.new ScanRow(entry));
		}
	}

	private void beginRename(String id) {
		renamingId = id;
		this.rebuildWidgets();
	}

	private void confirmRename() {
		if (renamingId != null && renameBox != null) {
			String newName = renameBox.getValue().strip();

			if (!newName.isEmpty()) {
				store.rename(renamingId, newName);
			}
		}

		renamingId = null;
		this.rebuildWidgets();
	}

	private ScanEntry findEntry(String id) {
		return store.list().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
	}

	private static String formatSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}

		if (bytes < 1024 * 1024) {
			return String.format("%.1f KB", bytes / 1024.0);
		}

		return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
	}

	private void copyPathToClipboard(Path path) {
		Minecraft.getInstance().keyboardHandler.setClipboard(path.toString());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 6, 0xFFFFFFFF, true);

		if (controller.isActive()) {
			String status = "A scan is currently running (" + controller.activeChunkCount() + " chunks captured).";
			String hint = "Manage it from the setup screen before browsing scans.";
			graphics.text(this.font, Component.literal(status), this.width / 2 - this.font.width(status) / 2, this.height / 2 - 54, 0xFF55FF55, true);
			graphics.text(this.font, Component.literal(hint), this.width / 2 - this.font.width(hint) / 2, this.height / 2 - 44, 0xFFAAAAAA, true);
			return;
		}

		if (!statusMessage.isEmpty() && renamingId == null) {
			String status = this.font.plainSubstrByWidth(statusMessage, this.width - 16);
			graphics.text(this.font, Component.literal(status), this.width / 2 - this.font.width(status) / 2, 48, 0xFFAAAAAA, true);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	private final class ScanList extends AbstractSelectionList<ScanList.ScanRow> {
		ScanList(Minecraft client, int width, int height, int top, int itemHeight) {
			super(client, width, height, top, itemHeight);
		}

		@Override
		public int addEntry(ScanRow entry) {
			return super.addEntry(entry);
		}

		void clear() {
			this.clearEntries();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}

		final class ScanRow extends AbstractSelectionList.Entry<ScanRow> {
			private final ScanEntry entry;

			ScanRow(ScanEntry entry) {
				this.entry = entry;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
				int x = getX();
				int y = getY();
				int width = getWidth();
				int height = getHeight();

				if (hovered) {
					graphics.fill(x, y, x + width, y + height, 0x30FFFFFF);
				}

				String subtitle = entry.dimension().replace("minecraft:", "") + " * " + entry.chunkCount() + " chunks * "
						+ formatSize(entry.byteSize()) + " * " + TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(entry.timestampMillis()));

				if (entry.byteSize() > SIZE_WARNING_BYTES) {
					subtitle += " * large scan";
				}

				int availableWidth = width - 8;
				String name = ScanBrowserScreen.this.font.plainSubstrByWidth(entry.name(), availableWidth);
				subtitle = ScanBrowserScreen.this.font.plainSubstrByWidth(subtitle, availableWidth);
				int subtitleColor = entry.byteSize() > SIZE_WARNING_BYTES ? 0xFFFFAA55 : 0xFFAAAAAA;

				graphics.text(ScanBrowserScreen.this.font, Component.literal(name), x + 4, y + 2, 0xFFFFFFFF, false);
				graphics.text(ScanBrowserScreen.this.font, Component.literal(subtitle), x + 4, y + 12, subtitleColor, false);

				drawAction(graphics, actionX(0), y, "Rename", 0xFFFFFFFF);
				drawAction(graphics, actionX(1), y, "Export", 0xFFFFFFFF);
				drawAction(graphics, actionX(2), y, "Bin", 0xFFFFFFFF);
				drawAction(graphics, actionX(3), y, "Delete", 0xFFFF5555);
			}

			private int actionX(int index) {
				return getX() + getWidth() - ACTION_WIDTH * (ACTION_COUNT - index) - 4;
			}

			private void drawAction(GuiGraphicsExtractor graphics, int x, int y, String label, int color) {
				graphics.text(ScanBrowserScreen.this.font, Component.literal(label), x, y + ACTION_ROW_Y_OFFSET, color, false);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				int localX = (int) (event.x() - getX());
				int localY = (int) (event.y() - getY());
				int width = getWidth();

				if (localY < ACTION_ROW_Y_OFFSET - 2) {
					return true;
				}

				for (int i = 0; i < ACTION_COUNT; i++) {
					int slotStart = width - ACTION_WIDTH * (ACTION_COUNT - i) - 4;
					int slotEnd = slotStart + ACTION_WIDTH;

					if (localX >= slotStart && localX < slotEnd) {
						handleAction(i);
						return true;
					}
				}

				return true;
			}

			private void handleAction(int index) {
				switch (index) {
					case 0 -> beginRename(entry.id());
					case 1 -> exportToFile();
					case 2 -> exportBinaryToFile();
					case 3 -> confirmDelete();
					default -> {
					}
				}
			}

			private void exportToFile() {
				Path path = store.exportToFile(entry.id());
				copyPathToClipboard(path);
				statusMessage = "Exported to " + path + " (path copied to clipboard).";
			}

			private void exportBinaryToFile() {
				Path path = store.exportBinaryToFile(entry.id());
				copyPathToClipboard(path);
				statusMessage = "Exported binary to " + path + " (path copied to clipboard).";
			}

			private void confirmDelete() {
				ScanBrowserScreen.this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
					if (confirmed) {
						store.delete(entry.id());
					}

					ScanBrowserScreen.this.minecraft.setScreen(ScanBrowserScreen.this);

					if (confirmed) {
						refreshList();
					}
				}, Component.literal("Delete Scan"),
						Component.literal("Are you sure you want to delete \"" + entry.name() + "\"? This cannot be undone.")));
			}
		}
	}
}
