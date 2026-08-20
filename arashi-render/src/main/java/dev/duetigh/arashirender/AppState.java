package dev.duetigh.arashirender;

import java.util.ArrayDeque;
import java.util.Deque;

import org.joml.Vector3f;

import dev.duetigh.arashirender.format.DecodedScan;
import dev.duetigh.arashirender.format.ScanFormatDecoder;
import dev.duetigh.arashirender.library.RenderLibrary;
import dev.duetigh.arashirender.palette.ColorPalette;
import dev.duetigh.arashirender.texture.TextureLoader;
import dev.duetigh.arashirender.texture.TextureSource;
import dev.duetigh.arashirender.update.UpdateChecker;
import dev.duetigh.arashirender.view.ScanIdentity;
import dev.duetigh.arashirender.view.SettingsStore;
import dev.duetigh.arashirender.view.ViewStore;
import dev.duetigh.arashirender.world.BlockEdit;
import dev.duetigh.arashirender.world.Filters;
import dev.duetigh.arashirender.world.VoxelWorld;

/** All non-GL, non-camera application state: the loaded scan, current filters, and their persistence. */
public final class AppState {
	public enum Screen {
		LIBRARY, VIEWER
	}

	public final ColorPalette palette = new ColorPalette();
	public final Filters filters = new Filters();
	public final RenderLibrary library = RenderLibrary.load();
	public final TextureLoader textureLoader = new TextureLoader();
	public final SettingsStore settings = SettingsStore.load();

	public Screen screen = Screen.LIBRARY;

	public VoxelWorld world;
	public DecodedScan scan;
	public String scanName = "";
	public String compactString = "";
	public String scanId = "";
	public long loadedAtMillis;
	public ViewStore viewStore;
	public String statusMessage = "";
	public boolean needsRebuild;

	/** Set from a background thread by {@code UpdateChecker.checkAsync} at startup; null if no update was found (yet). */
	public volatile UpdateChecker.UpdateInfo availableUpdate;
	/** True once the user has dismissed/actioned the update prompt this session, so it doesn't keep reopening. */
	public boolean updatePromptDismissed;

	/** The block the cursor last raycast-hit, packed via {@link VoxelWorld#pack}, or null. Not persisted - set by clicking. */
	public Long selectedKey;
	/** Set by {@link #centerView()} / center-on-load; Main consumes it each frame and applies it to the {@code Camera}. */
	public Vector3f pendingCameraTarget;

	private final Deque<BlockEdit> undoStack = new ArrayDeque<>();
	private final Deque<BlockEdit> redoStack = new ArrayDeque<>();

	public AppState() {
		if (settings.textureSourcePath != null) {
			try {
				textureLoader.setSource(settings.textureSourcePath);
			} catch (Exception ignored) {
				// Stale/missing path from a previous run - texture mode just falls back to color.
			}
		}

		if (!textureLoader.hasSource()) {
			String autoDetected = TextureSource.findLatestInstalledJar();

			if (autoDetected != null) {
				try {
					textureLoader.setSource(autoDetected);
				} catch (Exception ignored) {
					// No usable local install - texture mode just falls back to color.
				}
			}
		}
	}

	/** Decodes and loads a scan from its compact string, resetting filters and loading its saved views. Throws on malformed input. */
	public void loadFromCompactString(String compactString, String scanName) {
		DecodedScan decoded = ScanFormatDecoder.decode(compactString);
		VoxelWorld built = VoxelWorld.build(decoded);

		this.scan = decoded;
		this.world = built;
		this.compactString = compactString;
		this.scanName = scanName.isBlank() ? "Untitled scan" : scanName;
		this.scanId = ScanIdentity.forCompactString(compactString);
		this.loadedAtMillis = System.currentTimeMillis();
		this.viewStore = ViewStore.loadFor(scanId, this.scanName);
		this.filters.resetRangeTo(built);
		this.filters.setIsolateEnabled(false);
		this.selectedKey = null;
		this.undoStack.clear();
		this.redoStack.clear();

		for (String blockId : decoded.palette()) {
			if (ColorPalette.isAirLike(blockId)) {
				filters.setPaletteHidden(indexOf(blockId), true);
			}
		}

		if (settings.centerOnLoad) {
			centerView();
		}

		this.needsRebuild = true;
	}

	/** Points the pending camera target at the loaded world's bounding-box center; Main applies it next frame. */
	public void centerView() {
		if (world == null) {
			return;
		}

		float x = (world.minX() + world.maxX()) / 2f;
		float y = (world.minY() + world.maxY()) / 2f;
		float z = (world.minZ() + world.maxZ()) / 2f;
		pendingCameraTarget = new Vector3f(x, y, z);
	}

	public int indexOf(String blockId) {
		if (world == null) {
			return -1;
		}

		String[] palette = world.palette();

		for (int i = 0; i < palette.length; i++) {
			if (palette[i].equals(blockId)) {
				return i;
			}
		}

		return -1;
	}

	public boolean hasScan() {
		return world != null;
	}

	public boolean canUndo() {
		return !undoStack.isEmpty();
	}

	public boolean canRedo() {
		return !redoStack.isEmpty();
	}

	/** Deletes the currently selected block, if any, recording it for undo and clearing the redo stack. */
	public void deleteSelected() {
		if (selectedKey == null || world == null) {
			return;
		}

		int removed = world.removeCell(selectedKey);

		if (removed == -1) {
			return;
		}

		undoStack.push(new BlockEdit(selectedKey, removed));
		redoStack.clear();
		selectedKey = null;
		afterWorldMutation();
	}

	public void undo() {
		if (undoStack.isEmpty() || world == null) {
			return;
		}

		BlockEdit edit = undoStack.pop();
		world.putCell(edit.key(), edit.paletteIndex());
		redoStack.push(edit);
		afterWorldMutation();
	}

	public void redo() {
		if (redoStack.isEmpty() || world == null) {
			return;
		}

		BlockEdit edit = redoStack.pop();
		world.removeCell(edit.key());
		undoStack.push(edit);
		afterWorldMutation();
	}

	private void afterWorldMutation() {
		if (filters.isolateEnabled()) {
			filters.recomputeIsolate(world);
		}

		needsRebuild = true;
	}
}
