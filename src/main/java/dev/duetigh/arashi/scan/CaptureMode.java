package dev.duetigh.arashi.scan;

/** What a scan records: every block, only a whitelist of block types, or a seed block + its neighbors. */
public enum CaptureMode {
	EVERYTHING(0),
	WHITELIST(1),
	ISOLATE(2);

	private final int wireId;

	CaptureMode(int wireId) {
		this.wireId = wireId;
	}

	public int wireId() {
		return wireId;
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
