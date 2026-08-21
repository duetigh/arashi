package dev.duetigh.arashi.scan;

import java.util.function.BiConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import dev.duetigh.arashi.scanner.BlockScanner;

/**
 * Owns the single active {@link ScanSession}, if any. Chunks are captured only while a session is
 * active - one as they load via {@code CHUNK_LOAD}, plus whatever was already loaded at the moment
 * {@link #start()} was called (seeded from {@link BlockScanner#loadedChunks()}, the only source of
 * "all currently loaded chunks" available client-side) - in both cases restricted to the player's
 * actual video-settings render distance, the same circular cutoff {@code EspRenderer} uses, since
 * the client's loaded-chunk cache can otherwise extend a little past what render distance implies.
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

	/** Quick-start shortcut: full world height, no filtering. */
	public boolean start(ClientLevel level) {
		return start(level, level.getMinY(), level.getMaxY(), CaptureMode.EVERYTHING, CaptureParams.EVERYTHING);
	}

	/** Starts a scan with a user-chosen Y-range (silently clamped to the world's actual bounds) and capture mode. */
	public boolean start(ClientLevel level, int minY, int maxY, CaptureMode mode, CaptureParams params) {
		if (session != null) {
			return false;
		}

		int clampedMinY = Math.max(minY, level.getMinY());
		int clampedMaxY = Math.min(maxY, level.getMaxY());

		ScanSession newSession = new ScanSession(level.dimension().identifier().toString(), clampedMinY, clampedMaxY, mode, params);

		for (LevelChunk chunk : scanner.loadedChunks()) {
			if (withinRenderDistance(chunk.getPos())) {
				newSession.recordChunk(ChunkRecord.capture(chunk, newSession.minY(), newSession.maxY(), mode, params));
			}
		}

		session = newSession;
		return true;
	}

	/** Records a newly loaded chunk if a scan is active and the chunk is within render distance. No-op otherwise. */
	public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		ScanSession current = session;

		if (current != null && level.dimension().identifier().toString().equals(current.dimensionId())
				&& withinRenderDistance(chunk.getPos())) {
			current.recordChunk(ChunkRecord.capture(chunk, current.minY(), current.maxY(),
					current.captureMode(), current.captureParams()));
		}
	}

	/** Same circular cutoff {@code EspRenderer} uses, so a scan only ever covers what's actually visible. */
	private static boolean withinRenderDistance(ChunkPos pos) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null) {
			return true;
		}

		int renderDistanceBlocks = client.options.renderDistance().get() * 16;
		double centerX = pos.getMinBlockX() + 8.0;
		double centerZ = pos.getMinBlockZ() + 8.0;
		double dx = centerX - player.getX();
		double dz = centerZ - player.getZ();
		return dx * dx + dz * dz <= (double) renderDistanceBlocks * renderDistanceBlocks;
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

	/** The running session's settings, for a setup screen opened while a scan is already active. Null fields/session if inactive. */
	public ScanSession activeSession() {
		return session;
	}
}
