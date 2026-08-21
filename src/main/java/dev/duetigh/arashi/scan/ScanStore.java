package dev.duetigh.arashi.scan;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Persists saved scans to {@code config/arashi/scans/}: a single {@code index.json} listing
 * metadata (so the browser screen never needs to decompress a payload just to show a list) plus
 * one {@code <id>.bin} file per scan holding the deflate-compressed encoded payload.
 */
public final class ScanStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
			.withZone(ZoneId.systemDefault());

	private final Path dir;
	private final Path indexPath;
	private final List<ScanEntry> entries;

	private ScanStore(Path dir, List<ScanEntry> entries) {
		this.dir = dir;
		this.indexPath = dir.resolve("index.json");
		this.entries = entries;
	}

	public static ScanStore load() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("arashi").resolve("scans");
		Path indexPath = dir.resolve("index.json");

		if (!Files.exists(indexPath)) {
			return new ScanStore(dir, new ArrayList<>());
		}

		try (Reader reader = Files.newBufferedReader(indexPath, StandardCharsets.UTF_8)) {
			List<ScanEntry> loaded = GSON.fromJson(reader, new TypeToken<List<ScanEntry>>() { }.getType());
			return new ScanStore(dir, loaded != null ? loaded : new ArrayList<>());
		} catch (IOException e) {
			return new ScanStore(dir, new ArrayList<>());
		}
	}

	/** Newest-first list of saved scans. */
	public List<ScanEntry> list() {
		return entries.stream()
				.sorted(Comparator.comparingLong(ScanEntry::timestampMillis).reversed())
				.toList();
	}

	/** Encodes, compresses, and persists a scan session, defaulting its name to a formatted timestamp. */
	public ScanEntry save(ScanSession session) {
		byte[] compressed = ScanEncoder.compress(ScanEncoder.encode(session));
		String id = UUID.randomUUID().toString();
		String fileName = id + ".bin";
		String name = "Scan " + NAME_FORMAT.format(Instant.ofEpochMilli(session.startedAtMillis()));

		try {
			Files.createDirectories(dir);
			Files.write(dir.resolve(fileName), compressed);
		} catch (IOException e) {
			throw new RuntimeException("Failed to save scan to " + dir.resolve(fileName), e);
		}

		ScanEntry entry = new ScanEntry(id, name, session.startedAtMillis(), session.dimensionId(),
				session.chunkCount(), compressed.length, fileName);
		entries.add(entry);
		persistIndex();
		return entry;
	}

	public void rename(String id, String newName) {
		find(id).ifPresent(entry -> {
			entry.name = newName;
			persistIndex();
		});
	}

	public void delete(String id) {
		find(id).ifPresent(entry -> {
			entries.remove(entry);
			persistIndex();

			try {
				Files.deleteIfExists(dir.resolve(entry.fileName));
			} catch (IOException e) {
				throw new RuntimeException("Failed to delete scan file " + entry.fileName, e);
			}
		});
	}

	/** Deflate-compressed on-disk bytes for a saved scan (undo with {@code ScanEncoder}'s inverse, i.e. inflate). */
	public byte[] compressedBytes(String id) {
		ScanEntry entry = find(id).orElseThrow(() -> new IllegalArgumentException("Unknown scan id: " + id));

		try {
			return Files.readAllBytes(dir.resolve(entry.fileName));
		} catch (IOException e) {
			throw new RuntimeException("Failed to read scan file " + entry.fileName, e);
		}
	}

	/** Base64 copy-paste string for a saved scan. */
	public String compactString(String id) {
		return ScanEncoder.toCompactString(compressedBytes(id));
	}

	/**
	 * Writes a scan's compact string to a standalone {@code .txt} file under {@code config/arashi/scans/exports/}
	 * so it can be handed to arashi-render's "Open scan file..." picker instead of the clipboard - large scans
	 * (megabytes of base64) can hang or crash apps that treat a paste as ordinary text. Returns the written path.
	 */
	public Path exportToFile(String id) {
		ScanEntry entry = find(id).orElseThrow(() -> new IllegalArgumentException("Unknown scan id: " + id));
		Path exportsDir = dir.resolve("exports");
		String safeName = entry.name().replaceAll("[^a-zA-Z0-9 _-]", "_").strip();

		if (safeName.isEmpty()) {
			safeName = "scan";
		}

		Path target = exportsDir.resolve(safeName + ".txt");

		try {
			Files.createDirectories(exportsDir);
			Files.writeString(target, compactString(id), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Failed to export scan to " + target, e);
		}

		return target;
	}

	/**
	 * Writes a scan's raw compressed bytes (no base64) to a standalone {@code .arsb} file under
	 * {@code config/arashi/scans/exports/} - skips the ~33% base64 bloat and the giant-String decode
	 * cost that {@link #exportToFile} pays, at the cost of arashi-render needing a binary-aware
	 * import path. Returns the written path.
	 */
	public Path exportBinaryToFile(String id) {
		ScanEntry entry = find(id).orElseThrow(() -> new IllegalArgumentException("Unknown scan id: " + id));
		Path exportsDir = dir.resolve("exports");
		String safeName = entry.name().replaceAll("[^a-zA-Z0-9 _-]", "_").strip();

		if (safeName.isEmpty()) {
			safeName = "scan";
		}

		Path target = exportsDir.resolve(safeName + ".arsb");

		try {
			Files.createDirectories(exportsDir);
			Files.write(target, compressedBytes(id));
		} catch (IOException e) {
			throw new RuntimeException("Failed to export scan to " + target, e);
		}

		return target;
	}

	private java.util.Optional<ScanEntry> find(String id) {
		return entries.stream().filter(e -> e.id().equals(id)).findFirst();
	}

	private void persistIndex() {
		try {
			Files.createDirectories(dir);

			try (Writer writer = Files.newBufferedWriter(indexPath, StandardCharsets.UTF_8)) {
				GSON.toJson(entries, writer);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to save scan index to " + indexPath, e);
		}
	}
}
