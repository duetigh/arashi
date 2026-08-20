package dev.duetigh.arashi.scan;

import java.util.function.BiConsumer;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import dev.duetigh.arashi.scanner.BlockScanner;

/**
 * Owns the single active {@link ScanSession}, if any. Chunks are captured only while a session is
 * active - one as they load via {@code CHUNK_LOAD}, plus whatever was already loaded at the moment
 * {@link #start()} was called (seeded from {@link BlockScanner#loadedChunks()}, the only source of
 * "all currently loaded chunks" available client-side).
 */
public final class ScanController {
	private final BlockScanner scanner;
	private final ScanStore store;
	private volatile ScanSession session;
	private BiConsumer<ScanEntry, String> onStop;

	public ScanController(BlockScanner scanner, ScanStore store) {
		this.scanner = scanner;
		this.store = store;
	}

	/** Called when a scan auto-saves and stops (manual stop reports its own result via {@link #stop}'s return). */
	public void setOnStop(BiConsumer<ScanEntry, String> listener) {
		this.onStop = listener;
	}

	public boolean start(ClientLevel level) {
		if (session != null) {
			return false;
		}

		ScanSession newSession = new ScanSession(level.dimension().identifier().toString(), level.getMinY(), level.getMaxY());

		for (LevelChunk chunk : scanner.loadedChunks()) {
			newSession.recordChunk(ChunkRecord.capture(chunk, newSession.minY(), newSession.maxY()));
		}

		session = newSession;
		return true;
	}

	/** Records a newly loaded chunk if a scan is active. No-op otherwise. */
	public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		ScanSession current = session;

		if (current != null && level.dimension().identifier().toString().equals(current.dimensionId())) {
			current.recordChunk(ChunkRecord.capture(chunk, current.minY(), current.maxY()));
		}
	}

	/** Manual stop: saves (if any chunks were captured) and returns the resulting entry, or {@code null} if nothing to save. */
	public ScanEntry stop() {
		ScanSession current = session;
		session = null;

		if (current == null || current.chunkCount() == 0) {
			return null;
		}

		return store.save(current);
	}

	/**
	 * Auto-stop triggered by dimension change or disconnect - these aren't user-initiated, so the
	 * session is always saved (if it captured anything) rather than silently discarded.
	 */
	public void autoStop(String reason) {
		ScanEntry entry = stop();

		if (entry != null && onStop != null) {
			onStop.accept(entry, reason);
		}
	}

	/** Checks whether the active session's dimension still matches the given level, auto-stopping if not. */
	public void checkDimension(ClientLevel level) {
		ScanSession current = session;

		if (current != null && !level.dimension().identifier().toString().equals(current.dimensionId())) {
			autoStop("dimension change");
		}
	}

	public boolean isActive() {
		return session != null;
	}

	public int activeChunkCount() {
		ScanSession current = session;
		return current != null ? current.chunkCount() : 0;
	}
}
