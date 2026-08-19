package dev.duetigh.arashi.scanner;

import java.util.Set;

import net.minecraft.core.BlockPos;

/** A cluster of matched blocks reported as a single vein, centered on the average of its members. */
public record VeinMatch(BlockPos center, int size) {
	static VeinMatch of(Set<BlockPos> cluster) {
		long sumX = 0;
		long sumY = 0;
		long sumZ = 0;

		for (BlockPos pos : cluster) {
			sumX += pos.getX();
			sumY += pos.getY();
			sumZ += pos.getZ();
		}

		int count = cluster.size();
		BlockPos center = new BlockPos(
				Math.round((float) sumX / count),
				Math.round((float) sumY / count),
				Math.round((float) sumZ / count));

		return new VeinMatch(center, count);
	}
}
