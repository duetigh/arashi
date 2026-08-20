package dev.duetigh.arashi.scan;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.level.ChunkPos;

/** In-memory recording of a single scan: which chunks have been captured since it was started. */
public final class ScanSession {
	private final String dimensionId;
	private final int minY;
	private final int maxY;
	private final long startedAtMillis;
	private final Map<ChunkPos, ChunkRecord> chunks = new ConcurrentHashMap<>();

	public ScanSession(String dimensionId, int minY, int maxY) {
		this.dimensionId = dimensionId;
		this.minY = minY;
		this.maxY = maxY;
		this.startedAtMillis = System.currentTimeMillis();
	}

	public void recordChunk(ChunkRecord record) {
		chunks.put(new ChunkPos(record.chunkX(), record.chunkZ()), record);
	}

	public boolean hasChunk(ChunkPos pos) {
		return chunks.containsKey(pos);
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

	public long startedAtMillis() {
		return startedAtMillis;
	}

	public int chunkCount() {
		return chunks.size();
	}

	public Map<ChunkPos, ChunkRecord> chunks() {
		return chunks;
	}
}
