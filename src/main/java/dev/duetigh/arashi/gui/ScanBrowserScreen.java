package dev.duetigh.arashi.gui;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.widget.ArashiButton;
import dev.duetigh.arashi.gui.widget.ArashiListRow;
import dev.duetigh.arashi.gui.widget.ArashiScrollPanel;
import dev.duetigh.arashi.gui.widget.ArashiTextField;
import dev.duetigh.arashi.scan.ScanController;
import dev.duetigh.arashi.scan.ScanEntry;
import dev.duetigh.arashi.scan.ScanStore;

/**
 * Browse, rename, delete, or export previously saved scans. While a scan is running, this screen
 * is locked to a banner pointing at {@link ScanSetupScreen} - the list/export/delete actions below
 * would otherwise operate on stale data (or race a scan that's still writing to disk).
 */
public final class ScanBrowserScreen extends ArashiScreen {
	private static final int ROW_HEIGHT = 44;
	private static final int NAME_ROW_HEIGHT = 18;
	private static final int ACTION_HEIGHT = 16;
	private static final int ACTION_GAP = 4;
	private static final int ACTION_COUNT = 4;
	private static final long SIZE_WARNING_BYTES = 300_000;
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
			.withZone(ZoneId.systemDefault());

	private final ScanController controller;
	private final ScanStore store;

	private ArashiScrollPanel list;
	private String renamingId;
	private ArashiTextField renameField;
	private String statusMessage = "";

	public ScanBrowserScreen(Screen parent, ScanController controller, ScanStore store) {
		super(Component.literal("Arashi - Scans"), parent);
		this.controller = controller;
		this.store = store;
	}

	@Override
	protected void buildWidgets() {
		if (controller.isActive()) {
			buildActiveScanBanner();
		} else {
			buildScanList();
		}
	}

	/** Locked-out view while a scan is running: nothing here can touch the scan list until it's stopped. */
	private void buildActiveScanBanner() {
		ArashiButton manageButton = track(new ArashiButton("Manage Running Scan", ArashiButton.Style.PRIMARY,
				b -> this.minecraft.setScreenAndShow(new ScanSetupScreen(this, controller, store))));
		manageButton.setBounds(this.width / 2 - 75, this.height / 2 - 30, 150, 20);

		ArashiButton doneButton = track(new ArashiButton("Done", ArashiButton.Style.SECONDARY, b -> onClose()));
		doneButton.setBounds(this.width / 2 - 40, this.height - 26, 80, 20);
	}

	private void buildScanList() {
		int margin = ArashiTheme.PADDING;

		ArashiButton makeScanButton = track(new ArashiButton("Make Scan", ArashiButton.Style.PRIMARY,
				b -> this.minecraft.setScreenAndShow(new ScanSetupScreen(this, controller, store))));
		makeScanButton.setBounds(this.width / 2 - 75, 24, 150, 20);

		int listTop = 54;

		if (renamingId != null) {
			listTop = 82;
			renameField = track(new ArashiTextField(findEntryName(renamingId), v -> { }));
			renameField.setBounds(this.width / 2 - 75, 54, 120, 20);

			ArashiButton okButton = track(new ArashiButton("OK", ArashiButton.Style.PRIMARY, b -> confirmRename()));
			okButton.setBounds(this.width / 2 + 47, 54, 28, 20);
		}

		list = track(new ArashiScrollPanel());
		list.setBounds(margin, listTop, this.width - margin * 2, this.height - listTop - 32 - margin);

		ArashiButton doneButton = track(new ArashiButton("Done", ArashiButton.Style.SECONDARY, b -> onClose()));
		doneButton.setBounds(this.width / 2 - 40, this.height - 26, 80, 20);

		refreshList();
	}

	private void refreshList() {
		list.clear();
		int y = 0;
		int listWidth = list.getWidth();
		int actionWidth = (listWidth - ACTION_GAP * (ACTION_COUNT - 1)) / ACTION_COUNT;

		for (ScanEntry entry : store.list()) {
			String subtitle = entry.dimension().replace("minecraft:", "") + " * " + entry.chunkCount() + " chunks * "
					+ formatSize(entry.byteSize()) + " * " + TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(entry.timestampMillis()));

			if (entry.byteSize() > SIZE_WARNING_BYTES) {
				subtitle += " * large scan";
			}

			ArashiListRow row = new ArashiListRow(entry.name(), subtitle, null);
			row.setSize(listWidth, NAME_ROW_HEIGHT);
			list.add(row, 0, y);

			int actionY = y + NAME_ROW_HEIGHT + 2;
			addAction(actionWidth, 0, actionY, "Rename", () -> beginRename(entry.id()));
			addAction(actionWidth, actionWidth + ACTION_GAP, actionY, "Export", () -> exportToFile(entry.id()));
			addAction(actionWidth, (actionWidth + ACTION_GAP) * 2, actionY, "Bin", () -> exportBinaryToFile(entry.id()));
			addAction(actionWidth, (actionWidth + ACTION_GAP) * 3, actionY, "Delete", () -> confirmDelete(entry));

			y += ROW_HEIGHT;
		}
	}

	private void addAction(int width, int relativeX, int relativeY, String label, Runnable action) {
		ArashiButton button = new ArashiButton(label, ArashiButton.Style.SECONDARY, b -> action.run());
		button.setSize(width, ACTION_HEIGHT);
		list.add(button, relativeX, relativeY);
	}

	private void beginRename(String id) {
		renamingId = id;
		this.rebuildWidgets();
	}

	private void confirmRename() {
		if (renamingId != null && renameField != null) {
			String newName = renameField.getValue().strip();

			if (!newName.isEmpty()) {
				store.rename(renamingId, newName);
			}
		}

		renamingId = null;
		this.rebuildWidgets();
	}

	private String findEntryName(String id) {
		return store.list().stream().filter(e -> e.id().equals(id)).findFirst().map(ScanEntry::name).orElse("");
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

	private void exportToFile(String id) {
		Path path = store.exportToFile(id);
		copyPathToClipboard(path);
		statusMessage = "Exported to " + path + " (path copied to clipboard).";
	}

	private void exportBinaryToFile(String id) {
		Path path = store.exportBinaryToFile(id);
		copyPathToClipboard(path);
		statusMessage = "Exported binary to " + path + " (path copied to clipboard).";
	}

	private void confirmDelete(ScanEntry entry) {
		this.minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				store.delete(entry.id());
			}

			this.minecraft.setScreenAndShow(this);

			if (confirmed) {
				refreshList();
			}
		}, Component.literal("Delete Scan"),
				Component.literal("Are you sure you want to delete \"" + entry.name() + "\"? This cannot be undone.")));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);

		if (controller.isActive()) {
			String status = "A scan is currently running (" + controller.activeChunkCount() + " chunks captured).";
			String hint = "Manage it from the setup screen before browsing scans.";
			ctx.text(this.font, Component.literal(status), this.width / 2 - this.font.width(status) / 2, this.height / 2 - 54, 0xFF55FF55, true);
			ctx.text(this.font, Component.literal(hint), this.width / 2 - this.font.width(hint) / 2, this.height / 2 - 44, ArashiTheme.TEXT_SECONDARY, true);
			return;
		}

		if (!statusMessage.isEmpty() && renamingId == null) {
			String status = this.font.plainSubstrByWidth(statusMessage, this.width - 16);
			ctx.text(this.font, Component.literal(status), this.width / 2 - this.font.width(status) / 2, 48, ArashiTheme.TEXT_SECONDARY, true);
		}
	}
}
