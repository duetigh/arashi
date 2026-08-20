package dev.duetigh.arashi.scan;

/** Index metadata for one saved scan. The heavy compressed payload lives in a separate {@code .bin} file. */
public final class ScanEntry {
	String id;
	String name;
	long timestampMillis;
	String dimension;
	int chunkCount;
	long byteSize;
	String fileName;

	ScanEntry(String id, String name, long timestampMillis, String dimension, int chunkCount, long byteSize, String fileName) {
		this.id = id;
		this.name = name;
		this.timestampMillis = timestampMillis;
		this.dimension = dimension;
		this.chunkCount = chunkCount;
		this.byteSize = byteSize;
		this.fileName = fileName;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public long timestampMillis() {
		return timestampMillis;
	}

	public String dimension() {
		return dimension;
	}

	public int chunkCount() {
		return chunkCount;
	}

	public long byteSize() {
		return byteSize;
	}
}
