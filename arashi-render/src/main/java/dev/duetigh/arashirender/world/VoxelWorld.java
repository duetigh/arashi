package dev.duetigh.arashirender.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.duetigh.arashirender.format.DecodedScan;

/**
 * A decoded scan expanded into a flat lookup of every recorded cell: packed (x,y,z) -> palette
 * index. Built once per loaded scan; the mesher and the raycaster both read from this.
 */
public final class VoxelWorld {
	private static final int X_BITS = 26;
	private static final int Z_BITS = 26;
	private static final int Y_BITS = 12;
	private static final long X_OFFSET = 1L << (X_BITS - 1);
	private static final long Y_OFFSET = 1L << (Y_BITS - 1);
	private static final long Z_OFFSET = 1L << (Z_BITS - 1);
	private static final long X_MASK = (1L << X_BITS) - 1;
	private static final long Y_MASK = (1L << Y_BITS) - 1;
	private static final long Z_MASK = (1L << Z_BITS) - 1;

	private final String dimensionId;
	private final int minY;
	private final int maxY;
	private final String[] palette;
	private final Map<Long, Integer> cells;
	private final int minX;
	private final int maxX;
	private final int minZ;
	private final int maxZ;
	private final int[] blockCountsByPalette;

	private VoxelWorld(String dimensionId, int minY, int maxY, String[] palette, Map<Long, Integer> cells,
			int minX, int maxX, int minZ, int maxZ, int[] blockCountsByPalette) {
		this.dimensionId = dimensionId;
		this.minY = minY;
		this.maxY = maxY;
		this.palette = palette;
		this.cells = cells;
		this.minX = minX;
		this.maxX = maxX;
		this.minZ = minZ;
		this.maxZ = maxZ;
		this.blockCountsByPalette = blockCountsByPalette;
	}

	public static VoxelWorld build(DecodedScan scan) {
		String[] palette = scan.palette().toArray(new String[0]);
		Map<Long, Integer> cells = new HashMap<>();
		int[] counts = new int[palette.length];

		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (DecodedScan.DecodedChunk chunk : scan.chunks()) {
			int baseX = chunk.chunkX() * 16;
			int baseZ = chunk.chunkZ() * 16;
			minX = Math.min(minX, baseX);
			maxX = Math.max(maxX, baseX + 15);
			minZ = Math.min(minZ, baseZ);
			maxZ = Math.max(maxZ, baseZ + 15);

			List<List<DecodedScan.Run>> sections = chunk.sections();

			for (int s = 0; s < sections.size(); s++) {
				int sectionMinY = scan.minY() + s * 16;
				int cellIndex = 0;

				for (DecodedScan.Run run : sections.get(s)) {
					counts[run.paletteIndex()] += run.length();

					for (int k = 0; k < run.length(); k++) {
						int x = cellIndex / 256;
						int z = (cellIndex / 16) % 16;
						int y = cellIndex % 16;
						cells.put(pack(baseX + x, sectionMinY + y, baseZ + z), run.paletteIndex());
						cellIndex++;
					}
				}
			}
		}

		if (minX == Integer.MAX_VALUE) {
			minX = maxX = minZ = maxZ = 0;
		}

		return new VoxelWorld(scan.dimensionId(), scan.minY(), scan.maxY(), palette, cells, minX, maxX, minZ, maxZ, counts);
	}

	public static long pack(int x, int y, int z) {
		long ux = (x + X_OFFSET) & X_MASK;
		long uy = (y + Y_OFFSET) & Y_MASK;
		long uz = (z + Z_OFFSET) & Z_MASK;
		return (ux << (Y_BITS + Z_BITS)) | (uy << Z_BITS) | uz;
	}

	/** Palette index at the given world position, or -1 if this cell wasn't recorded. */
	public int get(int x, int y, int z) {
		Integer value = cells.get(pack(x, y, z));
		return value != null ? value : -1;
	}

	public String dimensionId() {
		return dimensionId;
	}

	public int minY() {
		return minY;
	}

	public int maxY() {
		return maxY;
	}

	public String[] palette() {
		return palette;
	}

	public int paletteCount() {
		return palette.length;
	}

	public int countFor(int paletteIndex) {
		return blockCountsByPalette[paletteIndex];
	}

	public Map<Long, Integer> cells() {
		return cells;
	}

	public int minX() {
		return minX;
	}

	public int maxX() {
		return maxX;
	}

	public int minZ() {
		return minZ;
	}

	public int maxZ() {
		return maxZ;
	}

	public int totalBlockCount() {
		return cells.size();
	}

	public static int unpackX(long key) {
		return (int) ((key >>> (Y_BITS + Z_BITS)) & X_MASK) - (int) X_OFFSET;
	}

	public static int unpackY(long key) {
		return (int) ((key >>> Z_BITS) & Y_MASK) - (int) Y_OFFSET;
	}

	public static int unpackZ(long key) {
		return (int) (key & Z_MASK) - (int) Z_OFFSET;
	}
}
