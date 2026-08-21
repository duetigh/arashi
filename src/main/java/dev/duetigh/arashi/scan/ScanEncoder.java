package dev.duetigh.arashi.scan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

/**
 * Encodes a {@link ScanSession} into the Arashi compact scan format - see the "ARSH" wire format
 * spec. This is the interface contract with the standalone arashi-render app, which reimplements
 * the decode side independently (no shared code between the two projects), so any change here
 * must bump {@link #FORMAT_VERSION} and stay backward-compatible in what old decoders can read.
 */
public final class ScanEncoder {
	static final byte[] MAGIC = {'A', 'R', 'S', 'H'};
	static final int FORMAT_VERSION = 2;

	private ScanEncoder() {
	}

	/** Encodes the session to the raw (uncompressed) payload bytes. */
	public static byte[] encode(ScanSession session) {
		Map<Block, Integer> paletteIndex = buildPalette(session);
		List<Block> palette = new java.util.ArrayList<>(paletteIndex.keySet());

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try {
			out.write(MAGIC);
			out.write(FORMAT_VERSION);
			writeCaptureMode(out, session.captureMode(), session.captureParams());
			writeStr(out, session.dimensionId());
			writeI32(out, session.minY());
			writeI32(out, session.maxY());

			writeVarInt(out, palette.size());
			for (Block block : palette) {
				writeStr(out, BuiltInRegistries.BLOCK.getKey(block).toString());
			}

			writeVarInt(out, session.chunkCount());
			for (ChunkRecord record : session.chunks().values()) {
				writeI32(out, record.chunkX());
				writeI32(out, record.chunkZ());

				List<ChunkRecord.Run> runs = record.runs();
				writeVarInt(out, runs.size());

				for (ChunkRecord.Run run : runs) {
					writeVarInt(out, paletteIndex.get(run.block()));
					writeVarInt(out, run.length());
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return out.toByteArray();
	}

	/** Zlib-deflates raw payload bytes (from {@link #encode}) - this is what gets persisted to disk. */
	public static byte[] compress(byte[] rawPayload) {
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);

		try (DeflaterOutputStream deflate = new DeflaterOutputStream(compressed, deflater)) {
			deflate.write(rawPayload);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deflater.end();
		}

		return compressed.toByteArray();
	}

	/** Base64 URL-safe (no padding) encodes already-compressed bytes into a copyable string. */
	public static String toCompactString(byte[] compressedPayload) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(compressedPayload);
	}

	/** Convenience: encode + compress straight to the copyable string. */
	public static String encodeToCompactString(ScanSession session) {
		return toCompactString(compress(encode(session)));
	}

	private static Map<Block, Integer> buildPalette(ScanSession session) {
		Map<Block, Integer> index = new LinkedHashMap<>();

		for (ChunkRecord record : session.chunks().values()) {
			for (ChunkRecord.Run run : record.runs()) {
				index.computeIfAbsent(run.block(), b -> index.size());
			}
		}

		return index;
	}

	private static void writeCaptureMode(ByteArrayOutputStream out, CaptureMode mode, CaptureParams params) throws IOException {
		out.write(mode.wireId());

		switch (mode) {
			case EVERYTHING -> {
			}
			case WHITELIST -> {
				Set<Block> blocks = ((CaptureParams.Whitelist) params).blocks();
				writeVarInt(out, blocks.size());

				for (Block block : blocks) {
					writeStr(out, BuiltInRegistries.BLOCK.getKey(block).toString());
				}
			}
			case ISOLATE -> {
				CaptureParams.Isolate isolate = (CaptureParams.Isolate) params;
				writeStr(out, BuiltInRegistries.BLOCK.getKey(isolate.seed()).toString());
				out.write(isolate.connectivity());
			}
		}
	}

	static void writeVarInt(ByteArrayOutputStream out, int value) {
		while ((value & ~0x7F) != 0) {
			out.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}

		out.write(value);
	}

	static void writeI32(ByteArrayOutputStream out, int value) {
		out.write((value >>> 24) & 0xFF);
		out.write((value >>> 16) & 0xFF);
		out.write((value >>> 8) & 0xFF);
		out.write(value & 0xFF);
	}

	static void writeStr(ByteArrayOutputStream out, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

		if (bytes.length > 0xFFFF) {
			throw new IllegalArgumentException("String too long to encode: " + value.length() + " chars");
		}

		out.write((bytes.length >>> 8) & 0xFF);
		out.write(bytes.length & 0xFF);
		out.writeBytes(bytes);
	}
}
