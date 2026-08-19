package dev.duetigh.arashi.scanner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;

/**
 * Groups block positions into veins: two positions belong to the same vein if they're
 * adjacent or separated by no more than a 1-block gap (Chebyshev distance <= 2).
 */
final class VeinClusterer {
	private static final int MAX_GAP = 1;
	private static final int CONNECT_DISTANCE = MAX_GAP + 1;

	private VeinClusterer() {
	}

	static List<Set<BlockPos>> cluster(Set<BlockPos> positions) {
		List<Set<BlockPos>> clusters = new ArrayList<>();
		Set<BlockPos> remaining = new HashSet<>(positions);

		while (!remaining.isEmpty()) {
			BlockPos seed = remaining.iterator().next();
			remaining.remove(seed);

			Set<BlockPos> cluster = new HashSet<>();
			Deque<BlockPos> queue = new ArrayDeque<>();
			queue.add(seed);

			while (!queue.isEmpty()) {
				BlockPos pos = queue.poll();
				cluster.add(pos);

				for (int dx = -CONNECT_DISTANCE; dx <= CONNECT_DISTANCE; dx++) {
					for (int dy = -CONNECT_DISTANCE; dy <= CONNECT_DISTANCE; dy++) {
						for (int dz = -CONNECT_DISTANCE; dz <= CONNECT_DISTANCE; dz++) {
							if (dx == 0 && dy == 0 && dz == 0) {
								continue;
							}

							BlockPos neighbor = pos.offset(dx, dy, dz);

							if (remaining.remove(neighbor)) {
								queue.add(neighbor);
							}
						}
					}
				}
			}

			clusters.add(cluster);
		}

		return clusters;
	}
}
