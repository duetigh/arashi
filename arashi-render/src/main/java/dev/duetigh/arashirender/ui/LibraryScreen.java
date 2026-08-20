package dev.duetigh.arashirender.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import dev.duetigh.arashirender.AppState;
import dev.duetigh.arashirender.library.RenderLibraryEntry;
import dev.duetigh.arashirender.update.BrowserLauncher;
import dev.duetigh.arashirender.update.UpdateChecker;

/**
 * The landing screen: a searchable list of every render ever imported (see {@link dev.duetigh.arashirender.library.RenderLibrary}),
 * plus the same import controls as {@link UiPanel}'s scan section. Shown first on launch; {@code UiPanel}
 * has a "< Library" button to come back here without unloading the current scan.
 */
public final class LibraryScreen {
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
			.withZone(ZoneId.systemDefault());

	private final ImString searchBuffer = new ImString(64);
	private final ImString importNameBuffer = new ImString(64);
	private boolean updatePopupOpened;

	public void draw(AppState state) {
		ImGui.setNextWindowPos(0, 0);
		ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(), ImGui.getIO().getDisplaySizeY());
		ImGui.begin("Arashi Render Library", ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove
				| ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoTitleBar);

		ImGui.text("Arashi Render");
		ImGui.textDisabled("Your saved renders. Search, open, or import a new one.");
		ImGui.separator();

		drawImportSection(state);
		ImGui.separator();
		drawEntryList(state);
		drawUpdatePrompt(state);

		ImGui.end();
	}

	private void drawUpdatePrompt(AppState state) {
		UpdateChecker.UpdateInfo update = state.availableUpdate;

		if (update == null || state.updatePromptDismissed) {
			return;
		}

		if (!updatePopupOpened) {
			ImGui.openPopup("Update Available");
			updatePopupOpened = true;
		}

		if (ImGui.beginPopupModal("Update Available")) {
			ImGui.text("A new version of Arashi Render is available:");
			ImGui.text(update.currentVersion() + "  ->  " + update.newVersion());
			ImGui.spacing();

			if (ImGui.button("Update")) {
				BrowserLauncher.open(update.releaseUrl());
			}

			ImGui.sameLine();

			if (ImGui.button("View changelog")) {
				ImGui.openPopup("Changelog");
			}

			ImGui.sameLine();

			if (ImGui.button("Not now")) {
				state.updatePromptDismissed = true;
				ImGui.closeCurrentPopup();
			}

			drawChangelogPopup(update);

			ImGui.endPopup();
		}
	}

	private void drawChangelogPopup(UpdateChecker.UpdateInfo update) {
		ImGui.setNextWindowSize(520, 420, ImGuiCond.FirstUseEver);

		if (ImGui.beginPopupModal("Changelog")) {
			ImGui.beginChild("changelogText", 0, 340, true);
			ImGui.textWrapped(update.changelog());
			ImGui.endChild();

			if (ImGui.button("Close")) {
				ImGui.closeCurrentPopup();
			}

			ImGui.endPopup();
		}
	}

	private void drawImportSection(AppState state) {
		ImGui.text("Import a new render");
		ImGui.inputText("Name (optional)", importNameBuffer);

		if (ImportControls.draw(state, importNameBuffer.get())) {
			importNameBuffer.set("");
			state.screen = AppState.Screen.VIEWER;
		}
	}

	private void drawEntryList(AppState state) {
		ImGui.text("Your renders");
		ImGui.inputText("Search", searchBuffer);
		String needle = searchBuffer.get().toLowerCase();

		List<RenderLibraryEntry> entries = state.library.entries().stream()
				.filter(entry -> needle.isBlank()
						|| entry.name.toLowerCase().contains(needle)
						|| entry.dimensionId.toLowerCase().contains(needle))
				.sorted(Comparator.comparingLong((RenderLibraryEntry e) -> e.lastOpenedMillis).reversed())
				.collect(Collectors.toList());

		if (entries.isEmpty()) {
			ImGui.textDisabled(state.library.entries().isEmpty()
					? "No renders yet - import one above to get started."
					: "No renders match your search.");
			return;
		}

		ImGui.beginChild("libraryList", 0, 0, true);

		for (RenderLibraryEntry entry : entries) {
			ImGui.text(entry.name);
			ImGui.sameLine(220);
			ImGui.textDisabled(entry.dimensionId + "  •  " + entry.blockCount + " blocks  •  "
					+ DATE_FORMAT.format(Instant.ofEpochMilli(entry.savedAtMillis)));

			if (ImGui.button("Open##" + entry.id)) {
				openEntry(state, entry);
			}

			ImGui.sameLine();

			if (ImGui.button("Delete##" + entry.id)) {
				state.library.delete(entry.id);
			}

			ImGui.separator();
		}

		ImGui.endChild();
	}

	private void openEntry(AppState state, RenderLibraryEntry entry) {
		String compactString = state.library.compactStringFor(entry.id);

		if (compactString == null) {
			state.statusMessage = "Failed to load render: saved data is missing.";
			return;
		}

		try {
			state.loadFromCompactString(compactString, entry.name);
			state.library.touchOpened(entry.id);
			state.statusMessage = "";
			state.screen = AppState.Screen.VIEWER;
		} catch (Exception e) {
			state.statusMessage = "Failed to load render: " + e.getMessage();
		}
	}
}
