package dev.duetigh.arashirender.library;

/** Metadata for one imported scan in the render library. The raw compact string is stored separately. */
public final class RenderLibraryEntry {
	public String id;
	public String name;
	public String dimensionId;
	public int chunkCount;
	public int blockCount;
	public long savedAtMillis;
	public long lastOpenedMillis;
	/** True if the payload is stored as raw compressed bytes ({@code <id>.arsb}) rather than a base64 string ({@code <id>.txt}). */
	public boolean binary;
}
