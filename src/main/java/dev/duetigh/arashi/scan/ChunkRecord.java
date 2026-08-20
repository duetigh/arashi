package dev.duetigh.arashi.scan;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Full-column block data for one chunk, captured as one run-length-encoded run list per 16-tall
 * section (bottom to top). Cell order within a section is x outer, z middle, y inner - matching
 * {@code BlockScanner.scanChunk}'s loop nesting - so long vertical air columns collapse into a
 * single run.
 */
public record ChunkRecord(int chunkX, int chunkZ, List<List<Run>> sections) {
	public record Run(Block block, int length) {
	}

	/** Captures the full column ({@code minY}..{@code maxY}, exclusive) of {@code chunk} into RLE runs. */
	public static ChunkRecord capture(LevelChunk chunk, int minY, int maxY) {
		ChunkPos pos = chunk.getPos();
		int sectionCount = (maxY - minY) / 16;
		List<List<Run>> sections = new ArrayList<>(sectionCount);

		for (int s = 0; s < sectionCount; s++) {
			int sectionMinY = minY + s * 16;
			List<Run> runs = new ArrayList<>();
			Block currentBlock = null;
			int runLength = 0;

			for (int x = 0; x < 16; x++) {
				int worldX = pos.getMinBlockX() + x;

				for (int z = 0; z < 16; z++) {
					int worldZ = pos.getMinBlockZ() + z;

					for (int y = 0; y < 16; y++) {
						Block block = chunk.getBlockState(new BlockPos(worldX, sectionMinY + y, worldZ)).getBlock();

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
				}
			}

			if (currentBlock != null) {
				runs.add(new Run(currentBlock, runLength));
			}

			sections.add(runs);
		}

		return new ChunkRecord(pos.x(), pos.z(), sections);
	}
}
