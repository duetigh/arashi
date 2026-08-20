package dev.duetigh.arashirender.world;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Current display filters for a loaded scan: Y-range, global opacity, per-block-type visibility,
 * and isolate mode (show only one block type plus its immediate neighbors). Visibility is resolved
 * against the *filtered* set (not raw world occupancy) so hiding a block type correctly reveals
 * whatever was behind it.
 */
public final class Filters {
	private static final int[][] NEIGHBORS_6 = {
			{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
	};
	private static final int[][] NEIGHBORS_26 = build26Neighbors();

	private int viewMinY;
	private int viewMaxY;
	private float globalOpacity = 1.0f;
	private final Set<Integer> hiddenPalette = new HashSet<>();
	private boolean isolateEnabled;
	private int isolateSeedIndex = -1;
	private int isolateConnectivity = 6;
	private Set<Long> isolateVisible = Set.of();

	public void resetRangeTo(VoxelWorld world) {
		viewMinY = world.minY();
		viewMaxY = world.maxY() - 1;
	}

	public int viewMinY() {
		return viewMinY;
	}

	public int viewMaxY() {
		return viewMaxY;
	}

	public void setViewRange(int min, int max) {
		this.viewMinY = min;
		this.viewMaxY = max;
	}

	public float globalOpacity() {
		return globalOpacity;
	}

	public void setGlobalOpacity(float opacity) {
		this.globalOpacity = opacity;
	}

	public boolean isPaletteHidden(int index) {
		return hiddenPalette.contains(index);
	}

	public void setPaletteHidden(int index, boolean hidden) {
		if (hidden) {
			hiddenPalette.add(index);
		} else {
			hiddenPalette.remove(index);
		}
	}

	public boolean isolateEnabled() {
		return isolateEnabled;
	}

	public void setIsolateEnabled(boolean enabled) {
		this.isolateEnabled = enabled;
	}

	public int isolateSeedIndex() {
		return isolateSeedIndex;
	}

	public void setIsolateSeedIndex(int index) {
		this.isolateSeedIndex = index;
	}

	public int isolateConnectivity() {
		return isolateConnectivity;
	}

	public void setIsolateConnectivity(int connectivity) {
		this.isolateConnectivity = connectivity;
	}

	/** Recomputes the isolate-mode visible set (seed cells + their immediate neighbors). Call after any isolate setting changes. */
	public void recomputeIsolate(VoxelWorld world) {
		if (!isolateEnabled || isolateSeedIndex < 0) {
			isolateVisible = Set.of();
			return;
		}

		int[][] offsets = isolateConnectivity >= 26 ? NEIGHBORS_26 : NEIGHBORS_6;
		Set<Long> visible = new HashSet<>();

		for (Map.Entry<Long, Integer> entry : world.cells().entrySet()) {
			if (entry.getValue() != isolateSeedIndex) {
				continue;
			}

			long key = entry.getKey();
			visible.add(key);
			int x = VoxelWorld.unpackX(key);
			int y = VoxelWorld.unpackY(key);
			int z = VoxelWorld.unpackZ(key);

			for (int[] offset : offsets) {
				int nx = x + offset[0];
				int ny = y + offset[1];
				int nz = z + offset[2];

				if (world.get(nx, ny, nz) != -1) {
					visible.add(VoxelWorld.pack(nx, ny, nz));
				}
			}
		}

		isolateVisible = visible;
	}

	/** Whether the cell at (x,y,z) with the given palette index should currently be drawn/selectable. */
	public boolean isVisible(long key, int x, int y, int z, int paletteIndex) {
		if (y < viewMinY || y > viewMaxY) {
			return false;
		}

		if (isolateEnabled) {
			return isolateVisible.contains(key);
		}

		return !hiddenPalette.contains(paletteIndex);
	}

	private static int[][] build26Neighbors() {
		int[][] offsets = new int[26][3];
		int i = 0;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}

					offsets[i++] = new int[]{dx, dy, dz};
				}
			}
		}

		return offsets;
	}
}
