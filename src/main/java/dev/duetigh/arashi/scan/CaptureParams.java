package dev.duetigh.arashi.scan;

import java.util.Set;

import net.minecraft.world.level.block.Block;

/** Mode-specific parameters for a scan's {@link CaptureMode}. */
public sealed interface CaptureParams {
	CaptureParams EVERYTHING = new Everything();

	record Everything() implements CaptureParams {
	}

	record Whitelist(Set<Block> blocks) implements CaptureParams {
	}

	record Isolate(Block seed, int connectivity) implements CaptureParams {
		public Isolate {
			if (connectivity != 6 && connectivity != 26) {
				throw new IllegalArgumentException("Isolate connectivity must be 6 or 26, got " + connectivity);
			}
		}
	}
}
