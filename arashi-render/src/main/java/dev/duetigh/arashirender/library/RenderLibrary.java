package dev.duetigh.arashirender.library;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.duetigh.arashirender.format.DecodedScan;
import dev.duetigh.arashirender.view.ScanIdentity;

/**
 * Persists the library of imported scans: an index file at {@code ~/.arashi-render/library/index.json}
 * plus one {@code <id>.txt} per entry holding its raw compact string (kept out of the index since
 * these can be large). Entry ids reuse {@link ScanIdentity}, so re-importing identical scan text
 * updates the existing entry rather than duplicating it.
 */
public final class RenderLibrary {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = Path.of(System.getProperty("user.home"), ".arashi-render", "library");
	private static final Path INDEX_PATH = DIR.resolve("index.json");

	private final List<RenderLibraryEntry> entries;

	private RenderLibrary(List<RenderLibraryEntry> entries) {
		this.entries = entries;
	}

	public static RenderLibrary load() {
		if (!Files.exists(INDEX_PATH)) {
			return new RenderLibrary(new ArrayList<>());
		}

		try (Reader reader = Files.newBufferedReader(INDEX_PATH, StandardCharsets.UTF_8)) {
			Data data = GSON.fromJson(reader, Data.class);
			return new RenderLibrary(data != null && data.entries != null ? data.entries : new ArrayList<>());
		} catch (IOException e) {
			return new RenderLibrary(new ArrayList<>());
		}
	}

	public List<RenderLibraryEntry> entries() {
		return entries;
	}

	/** Adds a new library entry for this scan, or refreshes an existing one's metadata if it's already present. */
	public RenderLibraryEntry upsert(String name, String compactString, DecodedScan decoded, int blockCount) {
		String id = ScanIdentity.forCompactString(compactString);
		RenderLibraryEntry entry = upsertMetadata(id, name, decoded, blockCount);
		entry.binary = false;

		try {
			Files.createDirectories(DIR);
			Files.writeString(DIR.resolve(id + ".txt"), compactString, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Failed to save render library entry " + id, e);
		}

		persist();
		return entry;
	}

	/** Binary-payload counterpart to {@link #upsert} - never materializes a base64 {@code String} for the payload. */
	public RenderLibraryEntry upsertBinary(String name, byte[] compressedPayload, DecodedScan decoded, int blockCount) {
		String id = ScanIdentity.forBytes(compressedPayload);
		RenderLibraryEntry entry = upsertMetadata(id, name, decoded, blockCount);
		entry.binary = true;

		try {
			Files.createDirectories(DIR);
			Files.write(DIR.resolve(id + ".arsb"), compressedPayload);
		} catch (IOException e) {
			throw new RuntimeException("Failed to save render library entry " + id, e);
		}

		persist();
		return entry;
	}

	private RenderLibraryEntry upsertMetadata(String id, String name, DecodedScan decoded, int blockCount) {
		RenderLibraryEntry entry = entries.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null);

		if (entry == null) {
			entry = new RenderLibraryEntry();
			entry.id = id;
			entry.savedAtMillis = System.currentTimeMillis();
			entries.add(entry);
		}

		entry.name = name;
		entry.dimensionId = decoded.dimensionId();
		entry.chunkCount = decoded.chunks().size();
		entry.blockCount = blockCount;
		entry.lastOpenedMillis = System.currentTimeMillis();
		return entry;
	}

	public void delete(String id) {
		entries.removeIf(entry -> entry.id.equals(id));

		try {
			Files.deleteIfExists(DIR.resolve(id + ".txt"));
			Files.deleteIfExists(DIR.resolve(id + ".arsb"));
		} catch (IOException ignored) {
			// Index no longer references it either way.
		}

		persist();
	}

	public String compactStringFor(String id) {
		try {
			return Files.readString(DIR.resolve(id + ".txt"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}

	public byte[] compressedBytesFor(String id) {
		try {
			return Files.readAllBytes(DIR.resolve(id + ".arsb"));
		} catch (IOException e) {
			return null;
		}
	}

	public void touchOpened(String id) {
		entries.stream().filter(e -> e.id.equals(id)).findFirst()
				.ifPresent(entry -> entry.lastOpenedMillis = System.currentTimeMillis());
		persist();
	}

	private void persist() {
		try {
			Files.createDirectories(DIR);
			Data data = new Data();
			data.entries = entries;

			try (Writer writer = Files.newBufferedWriter(INDEX_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to save render library index", e);
		}
	}

	private static final class Data {
		List<RenderLibraryEntry> entries;
	}
}
