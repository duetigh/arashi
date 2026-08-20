package dev.duetigh.arashirender;

import dev.duetigh.arashirender.format.DecodedScan;
import dev.duetigh.arashirender.format.ScanFormatDecoder;
import dev.duetigh.arashirender.palette.ColorPalette;
import dev.duetigh.arashirender.view.ScanIdentity;
import dev.duetigh.arashirender.view.ViewStore;
import dev.duetigh.arashirender.world.Filters;
import dev.duetigh.arashirender.world.VoxelWorld;

/** All non-GL, non-camera application state: the loaded scan, current filters, and their persistence. */
public final class AppState {
	public final ColorPalette palette = new ColorPalette();
	public final Filters filters = new Filters();

	public VoxelWorld world;
	public DecodedScan scan;
	public String scanName = "";
	public String compactString = "";
	public String scanId = "";
	public long loadedAtMillis;
	public ViewStore viewStore;
	public String statusMessage = "";
	public boolean needsRebuild;

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

		for (String blockId : decoded.palette()) {
			if (ColorPalette.isAirLike(blockId)) {
				filters.setPaletteHidden(indexOf(blockId), true);
			}
		}

		this.needsRebuild = true;
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
}
