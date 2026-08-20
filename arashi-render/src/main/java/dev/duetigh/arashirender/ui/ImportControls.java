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
 * render library, so anything ever opened - from either screen - shows up there.
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
				loaded = tryImport(state, clipboard, nameOverride, "Pasted scan");
			} else {
				state.statusMessage = "Clipboard is empty.";
			}
		}

		ImGui.sameLine();

		if (ImGui.button("Open scan file...")) {
			String path = TinyFileDialogs.tinyfd_openFileDialog("Open Arashi scan", "", null, "Scan text files", false);

			if (path != null) {
				try {
					String content = Files.readString(Path.of(path));
					String defaultName = Path.of(path).getFileName().toString();
					loaded = tryImport(state, content, nameOverride, defaultName);
				} catch (IOException e) {
					state.statusMessage = "Failed to read file: " + e.getMessage();
				}
			}
		}

		if (!state.statusMessage.isEmpty()) {
			ImGui.textColored(1.0f, 0.45f, 0.45f, 1.0f, state.statusMessage);
		}

		return loaded;
	}

	private static boolean tryImport(AppState state, String compactString, String nameOverride, String defaultName) {
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
}
