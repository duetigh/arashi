package dev.duetigh.arashi.scanner;

import java.util.Collection;
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
	private final Map<ChunkPos, LevelChunk> loadedChunks = new ConcurrentHashMap<>();
	private volatile Set<Block> trackedBlocks = Set.of();
	private volatile List<BlockPos> matchesSnapshot = List.of();
	private volatile ClientLevel currentLevel;

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
		rebuildSnapshot();
	}

	public void clear() {
		loadedChunks.clear();
		matchesByChunk.clear();
		rebuildSnapshot();
	}

	/** Immutable snapshot of all currently tracked block positions, safe to read from the render thread. */
	public List<BlockPos> matches() {
		return matchesSnapshot;
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
			rebuildSnapshot();
			return;
		}

		Set<BlockPos> found = new HashSet<>();
		int minY = level.getMinY();
		int maxY = level.getMaxY();

		for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
			for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
				for (int y = minY; y < maxY; y++) {
					BlockPos blockPos = new BlockPos(x, y, z);

					if (trackedBlocks.contains(chunk.getBlockState(blockPos).getBlock())) {
						found.add(blockPos);
					}
				}
			}
		}

		if (found.isEmpty()) {
			matchesByChunk.remove(pos);
		} else {
			matchesByChunk.put(pos, found);
		}

		rebuildSnapshot();
	}

	private void rebuildSnapshot() {
		List<BlockPos> all = matchesByChunk.values().stream()
				.flatMap(Set::stream)
				.toList();
		matchesSnapshot = all;
	}
}
