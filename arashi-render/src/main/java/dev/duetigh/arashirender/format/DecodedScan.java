package dev.duetigh.arashirender.format;

import java.util.List;

/**
 * Decoded form of an Arashi "ARSH" compact scan string. This is a from-scratch reimplementation of
 * the format the Arashi mod's {@code ScanEncoder} produces - the two projects share no code, only
 * the byte-level spec, so keep this in lockstep with that spec if it ever changes.
 */
public record DecodedScan(String dimensionId, int minY, int maxY, CaptureMode captureMode,
		CaptureParams captureParams, List<String> palette, List<DecodedChunk> chunks) {
	public record DecodedChunk(int chunkX, int chunkZ, List<Run> runs) {
	}

	public record Run(int paletteIndex, int length) {
	}

	/** Absent (null) for {@link CaptureMode#EVERYTHING}. */
	public sealed interface CaptureParams {
	}

	public record WhitelistParams(List<String> blockIds) implements CaptureParams {
	}

	public record IsolateParams(String seedBlockId, int connectivity) implements CaptureParams {
	}
}
