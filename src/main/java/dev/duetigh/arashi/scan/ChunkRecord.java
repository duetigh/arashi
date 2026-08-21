package dev.duetigh.arashi.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Full-column block data for one chunk, captured as a single run-length-encoded run list spanning
 * the whole {@code minY}..{@code maxY} column (no per-16-block-section reset). Cell order is x
 * outer, z middle, y inner - matching {@code BlockScanner.scanChunk}'s loop nesting - so long
 * vertical air columns collapse into a single run.
 */
public record ChunkRecord(int chunkX, int chunkZ, List<Run> runs) {
	public record Run(Block block, int length) {
	}

	private static final int[][] NEIGHBORS_6 = {
			{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
	};
	private static final int[][] NEIGHBORS_26 = build26Neighbors();

	/**
	 * Captures the full column ({@code minY}..{@code maxY}, exclusive) of {@code chunk} into RLE runs,
	 * applying {@code mode}'s block-type filtering before encoding.
	 */
	public static ChunkRecord capture(LevelChunk chunk, int minY, int maxY, CaptureMode mode, CaptureParams params) {
		ChunkPos pos = chunk.getPos();
		int columnHeight = maxY - minY;
		Block[] column = readColumn(chunk, pos, minY, columnHeight);

		switch (mode) {
			case WHITELIST -> applyWhitelist(column, ((CaptureParams.Whitelist) params).blocks());
			case ISOLATE -> applyIsolate(column, columnHeight, (CaptureParams.Isolate) params);
			case EVERYTHING -> {
			}
		}

		return new ChunkRecord(pos.x(), pos.z(), runLengthEncode(column));
	}

	private static Block[] readColumn(LevelChunk chunk, ChunkPos pos, int minY, int columnHeight) {
		Block[] column = new Block[16 * columnHeight * 16];
		int i = 0;

		for (int x = 0; x < 16; x++) {
			int worldX = pos.getMinBlockX() + x;

			for (int z = 0; z < 16; z++) {
				int worldZ = pos.getMinBlockZ() + z;

				for (int y = 0; y < columnHeight; y++) {
					column[i++] = chunk.getBlockState(new BlockPos(worldX, minY + y, worldZ)).getBlock();
				}
			}
		}

		return column;
	}

	private static void applyWhitelist(Block[] column, Set<Block> whitelist) {
		for (int i = 0; i < column.length; i++) {
			if (!whitelist.contains(column[i])) {
				column[i] = Blocks.AIR;
			}
		}
	}

	/**
	 * Marks the seed block and its immediate neighbors (within this chunk only - a simple per-chunk
	 * approximation that can miss connections crossing into not-yet-captured chunks) visible, then
	 * blanks everything else to air before encoding.
	 */
	private static void applyIsolate(Block[] column, int columnHeight, CaptureParams.Isolate isolate) {
		boolean[] visible = new boolean[column.length];
		int[][] offsets = isolate.connectivity() >= 26 ? NEIGHBORS_26 : NEIGHBORS_6;

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = 0; y < columnHeight; y++) {
					int i = index(x, z, y, columnHeight);

					if (column[i] != isolate.seed()) {
						continue;
					}

					visible[i] = true;

					for (int[] offset : offsets) {
						int nx = x + offset[0];
						int ny = y + offset[1];
						int nz = z + offset[2];

						if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16 || ny < 0 || ny >= columnHeight) {
							continue;
						}

						int ni = index(nx, nz, ny, columnHeight);

						if (column[ni] != Blocks.AIR) {
							visible[ni] = true;
						}
					}
				}
			}
		}

		for (int i = 0; i < column.length; i++) {
			if (!visible[i]) {
				column[i] = Blocks.AIR;
			}
		}
	}

	private static List<Run> runLengthEncode(Block[] column) {
		List<Run> runs = new ArrayList<>();
		Block currentBlock = null;
		int runLength = 0;

		for (Block block : column) {
			if (block == currentBlock) {
				runLength++;
			} else {
				if (currentBlock != null) {
					runs.add(new Run(currentBlock, runLength));
				}

				currentBlock = block;
				runLength = 1;
			}
		}

		if (currentBlock != null) {
			runs.add(new Run(currentBlock, runLength));
		}

		return runs;
	}

	private static int index(int x, int z, int y, int columnHeight) {
		return x * (16 * columnHeight) + z * columnHeight + y;
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
