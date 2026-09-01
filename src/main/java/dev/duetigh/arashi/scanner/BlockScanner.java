package dev.duetigh.arashi.scanner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Tracks, per loaded chunk column, the positions of blocks the player has selected to track.
 * Rescans happen only on chunk load/unload or when the tracked block set changes -
 * never on a per-tick basis.
 */
public final class BlockScanner {
	// A block this common somewhere in the tracked set would otherwise blow up detectNewVeins()'s
	// full re-cluster (see its javadoc) even at a throttled call rate - skip vein-announcement
	// clustering for a block once it has this many loaded positions rather than pay that cost.
	private static final int MAX_VEIN_DETECTION_POSITIONS = 5000;
	// Caps how many already-loaded chunks get (re)scanned per processPendingRescans() call, so a
	// bulk retrack (tracked-block set changed) spreads its cost over many ticks instead of scanning
	// every loaded chunk synchronously in one call - which, at a large render distance, was enough
	// to hang/crash the client.
	private static final int RESCAN_CHUNKS_PER_BATCH = 8;

	private final Map<ChunkPos, Map<BlockPos, Block>> matchesByChunk = new ConcurrentHashMap<>();
	private final Map<ChunkPos, Map<Block, Integer>> countsByChunk = new ConcurrentHashMap<>();
	private final Map<ChunkPos, LevelChunk> loadedChunks = new ConcurrentHashMap<>();
	private final Map<Block, Set<BlockPos>> announcedPositions = new ConcurrentHashMap<>();
	private final Deque<LevelChunk> pendingRescan = new ArrayDeque<>();
	private volatile Set<Block> trackedBlocks = Set.of();
	private volatile List<BlockPos> matchesSnapshot = List.of();
	private volatile Map<BlockPos, Block> matchesWithBlocksSnapshot = Map.of();
	private volatile Map<ChunkPos, Map<Block, Integer>> countsSnapshot = Map.of();
	private volatile ClientLevel currentLevel;
	private volatile boolean debugMode;
	private volatile boolean scanningSuppressed;
	private volatile NewMatchListener newMatchListener;

	/** Notified with veins that just entered tracking, grouped by block, so callers can e.g. announce them in chat. */
	public interface NewMatchListener {
		void onNewMatches(ClientLevel level, Map<Block, List<VeinMatch>> newVeins);
	}

	public void setNewMatchListener(NewMatchListener listener) {
		this.newMatchListener = listener;
	}

	public void setTrackedBlockIds(Collection<String> blockIds) {
		Set<Block> resolved = new HashSet<>();

		for (String id : blockIds) {
			BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).ifPresent(resolved::add);
		}

		this.trackedBlocks = resolved;
		announcedPositions.clear();
		// Matches for the old tracked set are stale the instant it changes - drop them now (rather
		// than showing wrong ESP/tracking results) and let processPendingRescans() repopulate them
		// for the new set a few chunks at a time.
		matchesByChunk.clear();
		countsByChunk.clear();
		rebuildSnapshot();
		queueRescanAllLoaded();
	}

	/** All chunks currently loaded client-side. There's no public API for this on {@code ClientChunkCache}, so callers that need it (e.g. seeding a scan with already-loaded chunks) piggyback on this bookkeeping. */
	public Collection<LevelChunk> loadedChunks() {
		return loadedChunks.values();
	}

	public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		currentLevel = level;
		loadedChunks.put(chunk.getPos(), chunk);
		scanChunk(level, chunk);
	}

	public void onChunkUnload(LevelChunk chunk) {
		ChunkPos key = chunk.getPos();
		loadedChunks.remove(key);
		Map<BlockPos, Block> removed = matchesByChunk.remove(key);
		countsByChunk.remove(key);

		if (removed != null) {
			for (Map.Entry<BlockPos, Block> entry : removed.entrySet()) {
				Set<BlockPos> announced = announcedPositions.get(entry.getValue());

				if (announced != null) {
					announced.remove(entry.getKey());
				}
			}
		}

		rebuildSnapshot();
	}

	public void clear() {
		loadedChunks.clear();
		matchesByChunk.clear();
		countsByChunk.clear();
		announcedPositions.clear();
		pendingRescan.clear();
		rebuildSnapshot();
	}

	public void setDebugMode(boolean enabled) {
		this.debugMode = enabled;
	}

	public boolean isDebugMode() {
		return debugMode;
	}

	/** While suppressed, chunk scans are skipped entirely - existing matches are frozen in place, not cleared. */
	public void setScanningSuppressed(boolean suppressed) {
		this.scanningSuppressed = suppressed;
	}

	public boolean isScanningSuppressed() {
		return scanningSuppressed;
	}

	/** Immutable snapshot of all currently tracked block positions, safe to read from the render thread. */
	public List<BlockPos> matches() {
		return matchesSnapshot;
	}

	/** Immutable snapshot of all currently tracked block positions with their block type, safe to read from the render thread. */
	public Map<BlockPos, Block> matchesWithBlocks() {
		return matchesWithBlocksSnapshot;
	}

	/** Immutable snapshot of tracked-block counts per loaded chunk, safe to read from the render thread. */
	public Map<ChunkPos, Map<Block, Integer>> debugCounts() {
		return countsSnapshot;
	}

	/**
	 * Rescans just the chunk at the given position. There is no vanilla/Fabric API event for a
	 * single block update on the client, so callers throttle this (e.g. once per second for the
	 * chunk the player currently stands in) to pick up nearby block changes without a full-area
	 * rescan every tick.
	 */
	public void rescanChunkAt(BlockPos pos) {
		ClientLevel level = currentLevel;

		if (level == null) {
			return;
		}

		LevelChunk chunk = loadedChunks.get(ChunkPos.containing(pos));

		if (chunk != null) {
			scanChunk(level, chunk);
		}
	}

	private void queueRescanAllLoaded() {
		pendingRescan.clear();
		pendingRescan.addAll(loadedChunks.values());
	}

	/**
	 * Drains up to {@link #RESCAN_CHUNKS_PER_BATCH} chunks from the queue {@link #setTrackedBlockIds}
	 * fills, actually performing their scans. Callers run this every tick so a bulk retrack across a
	 * large render distance completes over a couple of seconds instead of freezing the client.
	 */
	public void processPendingRescans() {
		ClientLevel level = currentLevel;

		if (level == null) {
			pendingRescan.clear();
			return;
		}

		for (int i = 0; i < RESCAN_CHUNKS_PER_BATCH && !pendingRescan.isEmpty(); i++) {
			LevelChunk chunk = pendingRescan.poll();

			if (loadedChunks.containsKey(chunk.getPos())) {
				scanChunk(level, chunk);
			}
		}
	}

	private void scanChunk(ClientLevel level, LevelChunk chunk) {
		if (scanningSuppressed) {
			return;
		}

		ChunkPos pos = chunk.getPos();

		if (trackedBlocks.isEmpty()) {
			matchesByChunk.remove(pos);
			countsByChunk.remove(pos);
			rebuildSnapshot();
			return;
		}

		Map<BlockPos, Block> found = new HashMap<>();
		Map<Block, Integer> counts = new HashMap<>();
		int minY = level.getMinY();
		int maxY = level.getMaxY();

		for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
			for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
				for (int y = minY; y < maxY; y++) {
					BlockPos blockPos = new BlockPos(x, y, z);
					Block block = chunk.getBlockState(blockPos).getBlock();

					if (trackedBlocks.contains(block)) {
						found.put(blockPos, block);
						counts.merge(block, 1, Integer::sum);
					}
				}
			}
		}

		if (found.isEmpty()) {
			matchesByChunk.remove(pos);
			countsByChunk.remove(pos);
		} else {
			matchesByChunk.put(pos, found);
			countsByChunk.put(pos, counts);
		}

		rebuildSnapshot();
	}

	/**
	 * Runs new-vein detection against whatever's currently loaded. Deliberately not called from
	 * {@link #scanChunk} itself - that fires once per chunk load, and during the initial burst of a
	 * world join that can mean hundreds of calls in a couple of seconds. Since detectNewVeins()
	 * re-clusters the *entire* accumulated match set from scratch every time, doing that on every
	 * single chunk load made tracking a common block (e.g. coal ore) freeze the client during world
	 * join. Callers instead trigger this on a throttled cadence (see ArashiClient's incremental
	 * rescan), which caps it to once per second regardless of how many chunks load in between.
	 */
	public void detectNewVeinsNow() {
		ClientLevel level = currentLevel;

		if (level != null) {
			detectNewVeins(level);
		}
	}

	/**
	 * Clusters the current global match set into veins (blocks touching or within a 1-block gap of
	 * each other) and reports only clusters that don't overlap any previously announced vein. A vein
	 * that grows after its first full scan is absorbed silently rather than re-announced.
	 */
	private void detectNewVeins(ClientLevel level) {
		NewMatchListener listener = newMatchListener;

		if (listener == null) {
			return;
		}

		Map<Block, Set<BlockPos>> positionsByBlock = new HashMap<>();

		for (Map.Entry<BlockPos, Block> entry : matchesWithBlocksSnapshot.entrySet()) {
			positionsByBlock.computeIfAbsent(entry.getValue(), b -> new HashSet<>()).add(entry.getKey());
		}

		Map<Block, List<VeinMatch>> newVeins = new HashMap<>();

		for (Map.Entry<Block, Set<BlockPos>> entry : positionsByBlock.entrySet()) {
			Block block = entry.getKey();
			Set<BlockPos> positions = entry.getValue();

			// A block this common (e.g. coal ore) isn't a useful "new vein" announcement anyway -
			// skip it rather than re-clustering tens of thousands of positions every cycle.
			if (positions.size() > MAX_VEIN_DETECTION_POSITIONS) {
				continue;
			}

			Set<BlockPos> announced = announcedPositions.computeIfAbsent(block, b -> ConcurrentHashMap.newKeySet());

			for (Set<BlockPos> cluster : VeinClusterer.cluster(positions)) {
				boolean isNew = cluster.stream().noneMatch(announced::contains);
				announced.addAll(cluster);

				if (isNew) {
					newVeins.computeIfAbsent(block, b -> new ArrayList<>()).add(VeinMatch.of(cluster));
				}
			}
		}

		if (!newVeins.isEmpty()) {
			listener.onNewMatches(level, newVeins);
		}
	}

	private void rebuildSnapshot() {
		Map<BlockPos, Block> merged = new HashMap<>();

		for (Map<BlockPos, Block> chunkMatches : matchesByChunk.values()) {
			merged.putAll(chunkMatches);
		}

		matchesWithBlocksSnapshot = Map.copyOf(merged);
		matchesSnapshot = List.copyOf(merged.keySet());
		countsSnapshot = Map.copyOf(countsByChunk);
	}
}
