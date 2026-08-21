package dev.duetigh.arashi.scan;

import java.util.List;

/**
 * Decoded form of the "ARSH" compact scan format - mirrors what a completely independent decoder
 * (e.g. arashi-render) would reconstruct from a compact string, using registry-id strings rather
 * than live {@code Block} references since decoders never depend on this project.
 */
public record DecodedScan(String dimensionId, int minY, int maxY, CaptureMode captureMode,
		DecodedCaptureParams captureParams, List<String> palette, List<DecodedChunk> chunks) {
	public record DecodedChunk(int chunkX, int chunkZ, List<DecodedRun> runs) {
	}

	public record DecodedRun(int paletteIndex, int length) {
	}

	/** Registry-id-string form of {@link CaptureParams}, absent (null) for {@link CaptureMode#EVERYTHING}. */
	public sealed interface DecodedCaptureParams {
	}

	public record WhitelistParams(List<String> blockIds) implements DecodedCaptureParams {
	}

	public record IsolateParams(String seedBlockId, int connectivity) implements DecodedCaptureParams {
	}
}
