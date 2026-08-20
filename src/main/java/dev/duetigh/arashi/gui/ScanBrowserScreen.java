package dev.duetigh.arashi.gui;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.duetigh.arashi.scan.ScanController;
import dev.duetigh.arashi.scan.ScanEntry;
import dev.duetigh.arashi.scan.ScanStore;

/** Start/stop scanning, and browse, rename, delete, or copy previously saved scans. */
public final class ScanBrowserScreen extends Screen {
	private static final int ROW_HEIGHT = 36;
	private static final int ACTION_ROW_Y_OFFSET = 24;
	private static final int SIZE_WARNING_BYTES = 300_000;
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
			.withZone(ZoneId.systemDefault());

	private final Screen parent;
	private final ScanController controller;
	private final ScanStore store;

	private Button startStopButton;
	private ScanList list;
	private String renamingId;
	private EditBox renameBox;
	private String pendingDeleteId;
	private String statusMessage = "";

	public ScanBrowserScreen(Screen parent, ScanController controller, ScanStore store) {
		super(Component.literal("Arashi - Scans"));
		this.parent = parent;
		this.controller = controller;
		this.store = store;
	}

	@Override
	protected void init() {
		startStopButton = this.addRenderableWidget(Button.builder(startStopLabel(), b -> toggleScan())
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

	private void toggleScan() {
		if (controller.isActive()) {
			ScanEntry entry = controller.stop();
			statusMessage = entry != null ? "Saved \"" + entry.name() + "\" (" + entry.chunkCount() + " chunks)." : "Scan discarded (no chunks captured).";
			refreshList();
		} else if (this.minecraft.level != null) {
			controller.start(this.minecraft.level);
			statusMessage = "";
		} else {
			statusMessage = "You must be in a world to start a scan.";
		}

		startStopButton.setMessage(startStopLabel());
	}

	private Component startStopLabel() {
		return Component.literal(controller.isActive() ? "Stop Scan (saves)" : "Start Scan");
	}

	private void refreshList() {
		list.clear();

		for (ScanEntry entry : store.list()) {
			list.addEntry(list.new ScanRow(entry));
		}
	}

	private void beginRename(String id) {
		renamingId = id;
		pendingDeleteId = null;
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 6, 0xFFFFFFFF, true);

		String status = controller.isActive()
				? "Scanning... " + controller.activeChunkCount() + " chunks captured"
				: statusMessage;

		if (!status.isEmpty() && renamingId == null) {
			int color = controller.isActive() ? 0xFF55FF55 : 0xFFAAAAAA;
			graphics.text(this.font, Component.literal(status), this.width / 2 - this.font.width(status) / 2, 48, color, true);
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
			private static final int ACTION_WIDTH = 56;

			private final ScanEntry entry;

			ScanRow(ScanEntry entry) {
				this.entry = entry;
			}

			private boolean pendingDelete() {
				return entry.id().equals(pendingDeleteId);
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

				graphics.text(ScanBrowserScreen.this.font, Component.literal(entry.name()), x + 4, y + 2, 0xFFFFFFFF, false);
				graphics.text(ScanBrowserScreen.this.font, Component.literal(subtitle), x + 4, y + 12, 0xFFAAAAAA, false);

				if (pendingDelete()) {
					String confirm = "Confirm delete?";
					int confirmX = x + width - ACTION_WIDTH * 3 - 8;
					graphics.text(ScanBrowserScreen.this.font, Component.literal(confirm), confirmX, y + ACTION_ROW_Y_OFFSET, 0xFFFF5555, false);
				}

				drawAction(graphics, actionX(0), y, "Rename", 0xFFFFFFFF);
				drawAction(graphics, actionX(1), y, "Export", 0xFFFFFFFF);
				drawAction(graphics, actionX(2), y, pendingDelete() ? "Confirm" : "Delete", 0xFFFF5555);
			}

			private int actionX(int index) {
				return getX() + getWidth() - ACTION_WIDTH * (3 - index) - 4;
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

				if (localX >= width - ACTION_WIDTH * 3 - 4 && localX < width - ACTION_WIDTH * 2 - 4) {
					beginRename(entry.id());
					return true;
				}

				if (localX >= width - ACTION_WIDTH * 2 - 4 && localX < width - ACTION_WIDTH - 4) {
					this.exportToFile();
					return true;
				}

				if (localX >= width - ACTION_WIDTH - 4) {
					this.handleDeleteClick();
					return true;
				}

				return true;
			}

			private void exportToFile() {
				Path path = store.exportToFile(entry.id());
				statusMessage = "Exported to " + path + " - open it in arashi-render.";
			}

			private void handleDeleteClick() {
				if (pendingDelete()) {
					store.delete(entry.id());
					pendingDeleteId = null;
					refreshList();
				} else {
					pendingDeleteId = entry.id();
				}
			}
		}
	}
}
