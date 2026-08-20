package dev.duetigh.arashirender.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import dev.duetigh.arashirender.AppState;
import dev.duetigh.arashirender.palette.BlockNames;
import dev.duetigh.arashirender.view.ExportWriter;
import dev.duetigh.arashirender.view.SavedView;
import dev.duetigh.arashirender.world.Filters;
import dev.duetigh.arashirender.world.Raycaster;
import dev.duetigh.arashirender.world.VoxelWorld;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** The single ImGui control panel: scan loading, filters, block-under-cursor inspector, and saved views. */
public final class UiPanel {
	private final ImString searchBuffer = new ImString(64);
	private final ImString newViewNameBuffer = new ImString(64);

	public void draw(AppState state, Raycaster.Hit hit) {
		ImGui.setNextWindowSize(380, 640, imgui.flag.ImGuiCond.FirstUseEver);
		ImGui.setNextWindowPos(20, 20, imgui.flag.ImGuiCond.FirstUseEver);
		ImGui.begin("Arashi Render");

		drawScanSection(state);

		if (state.hasScan()) {
			ImGui.separator();
			drawFilters(state);
			ImGui.separator();
			drawInspector(state, hit);
			ImGui.separator();
			drawViews(state);
		}

		ImGui.end();
	}

	private void drawScanSection(AppState state) {
		ImGui.text("Scan");

		if (ImGui.button("Paste from clipboard")) {
			String clipboard = ImGui.getClipboardText();

			if (clipboard != null && !clipboard.isBlank()) {
				tryLoad(state, clipboard, "Pasted scan");
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
					String name = Path.of(path).getFileName().toString();
					tryLoad(state, content, name);
				} catch (IOException e) {
					state.statusMessage = "Failed to read file: " + e.getMessage();
				}
			}
		}

		if (!state.statusMessage.isEmpty()) {
			ImGui.textColored(1.0f, 0.45f, 0.45f, 1.0f, state.statusMessage);
		}

		if (state.hasScan()) {
			ImGui.text("Loaded: " + state.scanName);
			ImGui.text("Dimension: " + state.scan.dimensionId());
			ImGui.text("Chunks: " + state.scan.chunks().size() + "   Blocks: " + state.world.totalBlockCount());
			ImGui.text("Block types: " + state.world.paletteCount());
		} else {
			ImGui.textDisabled("No scan loaded.");
		}
	}

	private void tryLoad(AppState state, String compactString, String name) {
		try {
			state.loadFromCompactString(compactString, name);
			state.statusMessage = "";
		} catch (Exception e) {
			state.statusMessage = "Failed to load scan: " + e.getMessage();
		}
	}

	private void drawFilters(AppState state) {
		Filters filters = state.filters;
		VoxelWorld world = state.world;

		ImGui.text("Filters");

		int[] rangeMin = {filters.viewMinY()};
		int[] rangeMax = {filters.viewMaxY()};
		if (ImGui.dragIntRange2("Y Range", rangeMin, rangeMax, 1, world.minY(), world.maxY() - 1)) {
			filters.setViewRange(rangeMin[0], rangeMax[0]);
			state.needsRebuild = true;
		}

		float[] opacity = {filters.globalOpacity()};
		if (ImGui.sliderFloat("Opacity", opacity, 0.05f, 1.0f)) {
			filters.setGlobalOpacity(opacity[0]);
		}

		ImBoolean isolate = new ImBoolean(filters.isolateEnabled());
		if (ImGui.checkbox("Isolate mode", isolate)) {
			filters.setIsolateEnabled(isolate.get());
			filters.recomputeIsolate(state.world);
			state.needsRebuild = true;
		}

		if (filters.isolateEnabled()) {
			drawIsolateControls(state);
		} else {
			drawVisibilityChecklist(state);
		}
	}

	private void drawIsolateControls(AppState state) {
		Filters filters = state.filters;
		String[] palette = state.world.palette();
		String[] labels = new String[palette.length];

		for (int i = 0; i < palette.length; i++) {
			labels[i] = BlockNames.friendly(palette[i]) + " (" + state.world.countFor(i) + ")";
		}

		ImInt seed = new ImInt(Math.max(0, filters.isolateSeedIndex()));
		if (ImGui.combo("Seed block", seed, labels)) {
			filters.setIsolateSeedIndex(seed.get());
			filters.recomputeIsolate(state.world);
			state.needsRebuild = true;
		}

		ImBoolean is26 = new ImBoolean(filters.isolateConnectivity() >= 26);
		if (ImGui.checkbox("26-connectivity (unchecked = 6)", is26)) {
			filters.setIsolateConnectivity(is26.get() ? 26 : 6);
			filters.recomputeIsolate(state.world);
			state.needsRebuild = true;
		}
	}

	private void drawVisibilityChecklist(AppState state) {
		ImGui.inputText("Search", searchBuffer);
		String needle = searchBuffer.get().toLowerCase();

		ImGui.beginChild("blockList", 0, 220, true);
		String[] palette = state.world.palette();

		for (int i = 0; i < palette.length; i++) {
			String blockId = palette[i];
			String friendly = BlockNames.friendly(blockId);

			if (!needle.isBlank() && !blockId.toLowerCase().contains(needle) && !friendly.toLowerCase().contains(needle)) {
				continue;
			}

			int rgb = state.palette.colorFor(blockId);
			float[] color = {((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};

			if (ImGui.colorEdit3("##color" + i, color, imgui.flag.ImGuiColorEditFlags.NoInputs | imgui.flag.ImGuiColorEditFlags.NoLabel)) {
				state.palette.setColor(blockId, ((int) (color[0] * 255) << 16) | ((int) (color[1] * 255) << 8) | (int) (color[2] * 255));
				state.needsRebuild = true;
			}

			ImGui.sameLine();

			ImBoolean visible = new ImBoolean(!state.filters.isPaletteHidden(i));
			if (ImGui.checkbox(friendly + " (" + state.world.countFor(i) + ")##vis" + i, visible)) {
				state.filters.setPaletteHidden(i, !visible.get());
				state.needsRebuild = true;
			}
		}

		ImGui.endChild();
	}

	private void drawInspector(AppState state, Raycaster.Hit hit) {
		ImGui.text("Inspector");

		if (hit == null) {
			ImGui.textDisabled("No block under cursor.");
			return;
		}

		String blockId = state.world.palette()[hit.paletteIndex()];
		ImGui.text(BlockNames.friendly(blockId) + " (" + blockId + ")");
		ImGui.text("Position: " + hit.x() + ", " + hit.y() + ", " + hit.z());
		ImGui.text("Chunk: " + Math.floorDiv(hit.x(), 16) + ", " + Math.floorDiv(hit.z(), 16));
	}

	private void drawViews(AppState state) {
		ImGui.text("Views");
		ImGui.inputText("View name", newViewNameBuffer);
		ImGui.sameLine();

		if (ImGui.button("Save")) {
			String name = newViewNameBuffer.get().strip();

			if (!name.isEmpty()) {
				state.viewStore.save(toSavedView(state, name));
				newViewNameBuffer.set("");
			}
		}

		List<SavedView> views = new ArrayList<>(state.viewStore.views());

		for (SavedView view : views) {
			ImGui.text(view.name);
			ImGui.sameLine();

			if (ImGui.button("Load##" + view.name)) {
				applyView(state, view);
			}

			ImGui.sameLine();

			if (ImGui.button("Export##" + view.name)) {
				exportView(state, view);
			}

			ImGui.sameLine();

			if (ImGui.button("Delete##" + view.name)) {
				state.viewStore.delete(view.name);
			}
		}
	}

	private SavedView toSavedView(AppState state, String name) {
		SavedView view = new SavedView();
		view.name = name;
		view.minY = state.filters.viewMinY();
		view.maxY = state.filters.viewMaxY();
		view.opacity = state.filters.globalOpacity();
		view.isolateEnabled = state.filters.isolateEnabled();
		view.isolateConnectivity = state.filters.isolateConnectivity();
		view.isolateSeedBlockId = state.filters.isolateSeedIndex() >= 0 ? state.world.palette()[state.filters.isolateSeedIndex()] : null;

		String[] palette = state.world.palette();
		for (int i = 0; i < palette.length; i++) {
			if (state.filters.isPaletteHidden(i)) {
				view.hiddenBlockIds.add(palette[i]);
			}
		}

		return view;
	}

	private void applyView(AppState state, SavedView view) {
		Filters filters = state.filters;
		filters.setViewRange(view.minY, view.maxY);
		filters.setGlobalOpacity(view.opacity);
		filters.setIsolateEnabled(view.isolateEnabled);
		filters.setIsolateConnectivity(view.isolateConnectivity);
		filters.setIsolateSeedIndex(view.isolateSeedBlockId != null ? state.indexOf(view.isolateSeedBlockId) : -1);

		String[] palette = state.world.palette();
		for (int i = 0; i < palette.length; i++) {
			filters.setPaletteHidden(i, view.hiddenBlockIds.contains(palette[i]));
		}

		if (filters.isolateEnabled()) {
			filters.recomputeIsolate(state.world);
		}

		state.needsRebuild = true;
	}

	private void exportView(AppState state, SavedView view) {
		String path = TinyFileDialogs.tinyfd_saveFileDialog("Export view", view.name + ".md", null, "Markdown");

		if (path == null) {
			return;
		}

		Map<String, Integer> visibleCounts = computeVisibleCounts(state, view);

		try {
			ExportWriter.write(Path.of(path), state.scanName, state.scan.dimensionId(), state.loadedAtMillis,
					state.scan.chunks().size(), state.world.totalBlockCount(), state.world.paletteCount(), view, visibleCounts);
		} catch (IOException e) {
			state.statusMessage = "Failed to export view: " + e.getMessage();
		}
	}

	private Map<String, Integer> computeVisibleCounts(AppState state, SavedView view) {
		Filters temp = new Filters();
		temp.setViewRange(view.minY, view.maxY);
		temp.setIsolateEnabled(view.isolateEnabled);
		temp.setIsolateConnectivity(view.isolateConnectivity);
		temp.setIsolateSeedIndex(view.isolateSeedBlockId != null ? state.indexOf(view.isolateSeedBlockId) : -1);

		String[] palette = state.world.palette();
		for (int i = 0; i < palette.length; i++) {
			temp.setPaletteHidden(i, view.hiddenBlockIds.contains(palette[i]));
		}

		if (temp.isolateEnabled()) {
			temp.recomputeIsolate(state.world);
		}

		Map<String, Integer> counts = new HashMap<>();

		for (Map.Entry<Long, Integer> entry : state.world.cells().entrySet()) {
			long key = entry.getKey();
			int paletteIndex = entry.getValue();
			int x = VoxelWorld.unpackX(key);
			int y = VoxelWorld.unpackY(key);
			int z = VoxelWorld.unpackZ(key);

			if (temp.isVisible(key, x, y, z, paletteIndex)) {
				counts.merge(palette[paletteIndex], 1, Integer::sum);
			}
		}

		return counts;
	}
}
