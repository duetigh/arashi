package dev.duetigh.arashirender.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import imgui.ImGui;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

import dev.duetigh.arashirender.AppState;

/**
 * The "paste from clipboard" / "open scan file" button pair, shared by {@link UiPanel}'s scan section
 * and {@link LibraryScreen}'s import flow. Every successful load also registers the scan in the
 * render library, so anything ever opened - from either screen - shows up there. {@code .arsb} files
 * (the raw-binary export) are read as bytes and never turned into a base64 {@code String}; everything
 * else (pasted text, {@code .txt} files) goes through the existing text path.
 */
final class ImportControls {
	private ImportControls() {
	}

	/** Draws the buttons and, on a successful load, registers the scan in the library. Returns true if a scan was just loaded. */
	static boolean draw(AppState state, String nameOverride) {
		boolean loaded = false;

		if (ImGui.button("Paste from clipboard")) {
			String clipboard = ImGui.getClipboardText();

			if (clipboard != null && !clipboard.isBlank()) {
				loaded = tryImportText(state, clipboard, nameOverride, "Pasted scan");
			} else {
				state.statusMessage = "Clipboard is empty.";
			}
		}

		ImGui.sameLine();

		if (ImGui.button("Open scan file...")) {
			String startDir = state.settings.scanFolderPath;
			String path = TinyFileDialogs.tinyfd_openFileDialog("Open Arashi scan",
					startDir != null ? startDir + "/" : "", null, "Scan files", false);

			if (path != null) {
				Path file = Path.of(path);
				String defaultName = file.getFileName().toString();

				try {
					if (path.toLowerCase().endsWith(".arsb")) {
						loaded = tryImportBinary(state, Files.readAllBytes(file), nameOverride, defaultName);
					} else {
						loaded = tryImportText(state, Files.readString(file), nameOverride, defaultName);
					}
				} catch (IOException e) {
					state.statusMessage = "Failed to read file: " + e.getMessage();
				}
			}
		}

		if (ImGui.button("Scan folder...")) {
			String startDir = state.settings.scanFolderPath;
			String folder = TinyFileDialogs.tinyfd_selectFolderDialog("Select the folder to pull scans from",
					startDir != null ? startDir : "");

			if (folder != null) {
				state.settings.scanFolderPath = folder;
				state.settings.save();
			}
		}

		ImGui.sameLine();

		if (state.settings.scanFolderPath != null) {
			ImGui.textDisabled(state.settings.scanFolderPath);
		} else {
			ImGui.textDisabled("No scan folder linked - \"Open scan file...\" starts from the default location.");
		}

		if (!state.statusMessage.isEmpty()) {
			ImGui.textColored(1.0f, 0.45f, 0.45f, 1.0f, state.statusMessage);
		}

		return loaded;
	}

	private static boolean tryImportText(AppState state, String compactString, String nameOverride, String defaultName) {
		String name = nameOverride != null && !nameOverride.isBlank() ? nameOverride.strip() : defaultName;

		try {
			state.loadFromCompactString(compactString, name);
			state.library.upsert(state.scanName, compactString, state.scan, state.world.totalBlockCount());
			state.statusMessage = "";
			return true;
		} catch (Exception e) {
			state.statusMessage = "Failed to load scan: " + e.getMessage();
			return false;
		}
	}

	private static boolean tryImportBinary(AppState state, byte[] compressedPayload, String nameOverride, String defaultName) {
		String name = nameOverride != null && !nameOverride.isBlank() ? nameOverride.strip() : defaultName;

		try {
			state.loadFromCompressedBytes(compressedPayload, name);
			state.library.upsertBinary(state.scanName, compressedPayload, state.scan, state.world.totalBlockCount());
			state.statusMessage = "";
			return true;
		} catch (Exception e) {
			state.statusMessage = "Failed to load scan: " + e.getMessage();
			return false;
		}
	}
}
