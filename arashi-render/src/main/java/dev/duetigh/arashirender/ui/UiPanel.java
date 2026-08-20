package dev.duetigh.arashirender.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

import dev.duetigh.arashirender.AppState;
import dev.duetigh.arashirender.palette.BlockNames;
import dev.duetigh.arashirender.render.RenderMode;
import dev.duetigh.arashirender.texture.TextureSource;
import dev.duetigh.arashirender.view.ExportWriter;
import dev.duetigh.arashirender.view.SavedView;
import dev.duetigh.arashirender.world.Filters;
import dev.duetigh.arashirender.world.Raycaster;
import dev.duetigh.arashirender.world.VoxelWorld;

/** The single ImGui control panel: scan loading, filters, block-under-cursor inspector, and saved views. */
public final class UiPanel {
	private final ImString searchBuffer = new ImString(64);
	private final ImString newViewNameBuffer = new ImString(64);

	public void draw(AppState state, Raycaster.Hit hit) {
		ImGui.setNextWindowSize(380, 640, imgui.flag.ImGuiCond.FirstUseEver);
		ImGui.setNextWindowPos(20, 20, imgui.flag.ImGuiCond.FirstUseEver);
		ImGui.begin("Arashi Render");

		if (ImGui.button("< Library")) {
			state.screen = AppState.Screen.LIBRARY;
		}

		ImGui.separator();
		drawScanSection(state);

		if (state.hasScan()) {
			ImGui.separator();
			drawSettings(state);
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
		ImportControls.draw(state, null);

		if (state.hasScan()) {
			ImGui.text("Loaded: " + state.scanName);
			ImGui.text("Dimension: " + state.scan.dimensionId());
			ImGui.text("Chunks: " + state.scan.chunks().size() + "   Blocks: " + state.world.totalBlockCount());
			ImGui.text("Block types: " + state.world.paletteCount());
		} else {
			ImGui.textDisabled("No scan loaded.");
		}
	}

	private void drawSettings(AppState state) {
		ImGui.text("Settings");

		ImBoolean centerOnLoad = new ImBoolean(state.settings.centerOnLoad);
		if (ImGui.checkbox("Center view on load", centerOnLoad)) {
			state.settings.centerOnLoad = centerOnLoad.get();
			state.settings.save();
		}

		ImGui.sameLine();

		if (ImGui.button("Center view now")) {
			state.centerView();
		}

		ImInt mode = new ImInt(state.settings.renderMode == RenderMode.TEXTURE ? 1 : 0);
		if (ImGui.radioButton("Color##rendermode", mode, 0) || ImGui.radioButton("Texture##rendermode", mode, 1)) {
			state.settings.renderMode = mode.get() == 1 ? RenderMode.TEXTURE : RenderMode.COLOR;
			state.settings.save();
			state.needsRebuild = true;
		}

		if (ImGui.button("Texture source...")) {
			String startDir = TextureSource.defaultVersionsDir();
			String path = TinyFileDialogs.tinyfd_openFileDialog("Select a Minecraft version jar or resource pack",
					startDir != null ? startDir + "/" : "", null, "Minecraft jar/zip", false);

			if (path != null) {
				try {
					state.textureLoader.setSource(path);
					state.settings.textureSourcePath = path;
					state.settings.save();
					state.needsRebuild = true;
				} catch (Exception e) {
					state.statusMessage = "Failed to open texture source: " + e.getMessage();
				}
			}
		}

		ImGui.sameLine();

		if (state.textureLoader.hasSource()) {
			ImGui.textDisabled(state.textureLoader.sourcePath());
		} else {
			ImGui.textDisabled("No local Minecraft install found - falling back to colors.");
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

			if (isolate.get() && filters.isolateSeedIndex() < 0) {
				filters.setIsolateSeedIndex(0);
			}

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

		ImGui.beginDisabled(!state.canUndo());
		if (ImGui.button("Undo")) {
			state.undo();
		}
		ImGui.endDisabled();

		ImGui.sameLine();

		ImGui.beginDisabled(!state.canRedo());
		if (ImGui.button("Redo")) {
			state.redo();
		}
		ImGui.endDisabled();

		if (state.selectedKey != null) {
			ImGui.sameLine();

			if (ImGui.button("Delete selected")) {
				state.deleteSelected();
			}
		}

		if (hit == null) {
			ImGui.textDisabled("No block under cursor.");
			return;
		}

		String blockId = state.world.palette()[hit.paletteIndex()];
		ImGui.text(BlockNames.friendly(blockId) + " (" + blockId + ")");
		ImGui.text("Position: " + hit.x() + ", " + hit.y() + ", " + hit.z());
		ImGui.text("Chunk: " + Math.floorDiv(hit.x(), 16) + ", " + Math.floorDiv(hit.z(), 16));
		ImGui.textDisabled("Click to select, Delete to remove, Ctrl+Z/Ctrl+Y to undo/redo.");
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
