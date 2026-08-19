package dev.duetigh.arashi.scanner;

import java.util.ArrayList;
import java.util.Collection;
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
	private final Map<ChunkPos, Set<BlockPos>> matchesByChunk = new ConcurrentHashMap<>();
	private final Map<ChunkPos, Map<Block, Integer>> countsByChunk = new ConcurrentHashMap<>();
	private final Map<ChunkPos, LevelChunk> loadedChunks = new ConcurrentHashMap<>();
	private volatile Set<Block> trackedBlocks = Set.of();
	private volatile List<BlockPos> matchesSnapshot = List.of();
	private volatile Map<ChunkPos, Map<Block, Integer>> countsSnapshot = Map.of();
	private volatile ClientLevel currentLevel;
	private volatile boolean debugMode;
	private volatile NewMatchListener newMatchListener;

	/** Notified with positions that just entered tracking, grouped by block, so callers can e.g. announce them in chat. */
	public interface NewMatchListener {
		void onNewMatches(ClientLevel level, Map<Block, List<BlockPos>> newlyFound);
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
		rescanAllLoaded();
	}

	public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		currentLevel = level;
		loadedChunks.put(chunk.getPos(), chunk);
		scanChunk(level, chunk);
	}

	public void onChunkUnload(LevelChunk chunk) {
		ChunkPos key = chunk.getPos();
		loadedChunks.remove(key);
		matchesByChunk.remove(key);
		countsByChunk.remove(key);
		rebuildSnapshot();
	}

	public void clear() {
		loadedChunks.clear();
		matchesByChunk.clear();
		countsByChunk.clear();
		rebuildSnapshot();
	}

	public void setDebugMode(boolean enabled) {
		this.debugMode = enabled;
	}

	public boolean isDebugMode() {
		return debugMode;
	}

	/** Immutable snapshot of all currently tracked block positions, safe to read from the render thread. */
	public List<BlockPos> matches() {
		return matchesSnapshot;
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

	private void rescanAllLoaded() {
		ClientLevel level = currentLevel;

		if (level == null) {
			return;
		}

		for (LevelChunk chunk : loadedChunks.values()) {
			scanChunk(level, chunk);
		}
	}

	private void scanChunk(ClientLevel level, LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();

		if (trackedBlocks.isEmpty()) {
			matchesByChunk.remove(pos);
			countsByChunk.remove(pos);
			rebuildSnapshot();
			return;
		}

		Set<BlockPos> previous = matchesByChunk.getOrDefault(pos, Set.of());
		Set<BlockPos> found = new HashSet<>();
		Map<Block, Integer> counts = new HashMap<>();
		int minY = level.getMinY();
		int maxY = level.getMaxY();

		for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
			for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
				for (int y = minY; y < maxY; y++) {
					BlockPos blockPos = new BlockPos(x, y, z);
					Block block = chunk.getBlockState(blockPos).getBlock();

					if (trackedBlocks.contains(block)) {
						found.add(blockPos);
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
		notifyNewMatches(level, previous, found);
	}

	private void notifyNewMatches(ClientLevel level, Set<BlockPos> previous, Set<BlockPos> found) {
		NewMatchListener listener = newMatchListener;

		if (listener == null) {
			return;
		}

		Map<Block, List<BlockPos>> newlyFound = new HashMap<>();

		for (BlockPos blockPos : found) {
			if (!previous.contains(blockPos)) {
				Block block = level.getBlockState(blockPos).getBlock();
				newlyFound.computeIfAbsent(block, b -> new ArrayList<>()).add(blockPos);
			}
		}

		if (!newlyFound.isEmpty()) {
			listener.onNewMatches(level, newlyFound);
		}
	}

	private void rebuildSnapshot() {
		List<BlockPos> all = matchesByChunk.values().stream()
				.flatMap(Set::stream)
				.toList();
		matchesSnapshot = all;
		countsSnapshot = Map.copyOf(countsByChunk);
	}
}
