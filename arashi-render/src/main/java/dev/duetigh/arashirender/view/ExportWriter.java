package dev.duetigh.arashirender.view;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dev.duetigh.arashirender.palette.BlockNames;

/** Writes a saved view as a Markdown file a person (or an AI) can read and understand without opening the app. */
public final class ExportWriter {
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
			.withZone(ZoneId.of("UTC"));

	private ExportWriter() {
	}

	public static void write(Path outFile, String scanName, String dimensionId, long loadedAtMillis, int chunkCount,
			int totalBlockCount, int totalPaletteTypes, SavedView view, Map<String, Integer> visibleCountsByBlockId) throws IOException {
		StringBuilder md = new StringBuilder();

		md.append("# Arashi Scan Export - ").append(scanName).append("\n\n");
		// The compact scan format doesn't carry a capture timestamp, only the data itself - this is
		// when the scan was loaded into arashi-render, not when it was originally recorded in-game.
		md.append("- Scan loaded: ").append(TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(loadedAtMillis))).append('\n');
		md.append("- Dimension: ").append(dimensionId).append('\n');
		md.append("- Chunks recorded: ").append(chunkCount).append('\n');
		md.append("- Total blocks recorded: ").append(totalBlockCount).append('\n');
		md.append("- View: \"").append(view.name).append("\"\n\n");

		md.append("## View settings\n\n");
		md.append("- Y range: ").append(view.minY).append(" to ").append(view.maxY).append('\n');
		md.append("- Opacity: ").append(Math.round(view.opacity * 100)).append("%\n");

		if (view.isolateEnabled) {
			md.append("- Isolate mode: on - showing \"").append(view.isolateSeedBlockId)
					.append("\" and its ").append(view.isolateConnectivity).append("-connected neighbors\n");
		} else {
			md.append("- Isolate mode: off\n");
			int visibleTypes = totalPaletteTypes - view.hiddenBlockIds.size();
			md.append("- Visible block types: ").append(visibleTypes).append(" of ").append(totalPaletteTypes).append(" total types in this scan\n");
		}

		md.append("\n## Block legend (visible set only)\n\n");
		md.append("| Block ID | Display name | Count in view |\n");
		md.append("|---|---|---|\n");

		List<Map.Entry<String, Integer>> sorted = visibleCountsByBlockId.entrySet().stream()
				.sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
				.toList();

		for (Map.Entry<String, Integer> entry : sorted) {
			md.append("| ").append(entry.getKey()).append(" | ").append(BlockNames.friendly(entry.getKey()))
					.append(" | ").append(entry.getValue()).append(" |\n");
		}

		int totalVisible = visibleCountsByBlockId.values().stream().mapToInt(Integer::intValue).sum();
		md.append("\n## Summary\n\n");
		md.append(totalVisible).append(" blocks match the \"").append(view.name).append("\" view's filters across the ")
				.append(chunkCount).append("-chunk scan.\n");

		try (Writer writer = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
			writer.write(md.toString());
		}
	}
}
