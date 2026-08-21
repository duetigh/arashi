package dev.duetigh.arashirender.format;

/** Mirrors the Arashi mod's {@code CaptureMode} - what a scan recorded: everything, a whitelist, or an isolated seed block. */
public enum CaptureMode {
	EVERYTHING(0),
	WHITELIST(1),
	ISOLATE(2);

	private final int wireId;

	CaptureMode(int wireId) {
		this.wireId = wireId;
	}

	public static CaptureMode fromWireId(int wireId) {
		for (CaptureMode mode : values()) {
			if (mode.wireId == wireId) {
				return mode;
			}
		}

		throw new IllegalArgumentException("Unknown capture mode id: " + wireId);
	}
}
