package dev.duetigh.arashi.scanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * In tracking mode, follows the nearest vein of a single tracked block. {@link #checkTargetIntact}
 * is a cheap per-tick membership check that retargets immediately once the current vein starts
 * getting mined; {@link #recompute} is the fuller nearest-vein search, run on a throttled cadence
 * so "the player moved, so retarget to whatever's nearest now" doesn't re-cluster every tick.
 */
public final class TrackingController {
	// Culls candidates by distance before clustering, since only the nearest vein ever matters -
	// without this, tracking a very common block (e.g. coal ore) would try to cluster every match
	// across every loaded chunk each recompute, which is unbounded and can hang/crash the client.
	// Matches EspRenderer's own render-distance cutoff so the tracking line can always reach any
	// vein the box ESP is actually showing - a fixed short radius left the line missing whenever
	// the nearest vein was farther away than that (but still well within view).
	private static final int MAX_CANDIDATES = 3000;

	private final BlockScanner scanner;
	private volatile Block trackedBlock;
	private volatile Set<BlockPos> targetVeinBlocks = Set.of();
	private volatile BlockPos targetCenter;

	public TrackingController(BlockScanner scanner) {
		this.scanner = scanner;
	}

	/** Resets tracking state whenever the single block being tracked changes. */
	public void setTrackedBlock(Block block) {
		if (this.trackedBlock != block) {
			this.trackedBlock = block;
			targetVeinBlocks = Set.of();
			targetCenter = null;
		}
	}

	/** World-space center of the vein currently being tracked, or {@code null} if there's no target. */
	public BlockPos targetCenter() {
		return targetCenter;
	}

	public void checkTargetIntact(Vec3 playerPos) {
		Set<BlockPos> current = targetVeinBlocks;

		if (current.isEmpty()) {
			return;
		}

		Map<BlockPos, Block> matches = scanner.matchesWithBlocks();
		Block block = trackedBlock;

		for (BlockPos pos : current) {
			if (matches.get(pos) != block) {
				recompute(playerPos);
				return;
			}
		}
	}

	public void recompute(Vec3 playerPos) {
		Block block = trackedBlock;

		if (block == null) {
			targetVeinBlocks = Set.of();
			targetCenter = null;
			return;
		}

		List<BlockPos> nearby = new ArrayList<>();
		double renderDistanceBlocks = Minecraft.getInstance().options.renderDistance().get() * 16;
		double maxTrackRadiusSq = renderDistanceBlocks * renderDistanceBlocks;

		for (Map.Entry<BlockPos, Block> entry : scanner.matchesWithBlocks().entrySet()) {
			if (entry.getValue() != block) {
				continue;
			}

			BlockPos pos = entry.getKey();

			if (pos.distToCenterSqr(playerPos.x, playerPos.y, playerPos.z) <= maxTrackRadiusSq) {
				nearby.add(pos);
			}
		}

		if (nearby.isEmpty()) {
			targetVeinBlocks = Set.of();
			targetCenter = null;
			return;
		}

		if (nearby.size() > MAX_CANDIDATES) {
			nearby.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(playerPos.x, playerPos.y, playerPos.z)));
			nearby = nearby.subList(0, MAX_CANDIDATES);
		}

		Set<BlockPos> positions = new HashSet<>(nearby);
		List<Set<BlockPos>> clusters = VeinClusterer.cluster(positions);
		VeinMatch nearest = null;
		Set<BlockPos> nearestBlocks = null;
		double nearestDistSq = Double.MAX_VALUE;

		for (Set<BlockPos> cluster : clusters) {
			VeinMatch vein = VeinMatch.of(cluster);
			double distSq = vein.center().distToCenterSqr(playerPos.x, playerPos.y, playerPos.z);

			if (distSq < nearestDistSq) {
				nearestDistSq = distSq;
				nearest = vein;
				nearestBlocks = cluster;
			}
		}

		targetVeinBlocks = nearestBlocks != null ? Set.copyOf(nearestBlocks) : Set.of();
		targetCenter = nearest != null ? nearest.center() : null;
	}
}
