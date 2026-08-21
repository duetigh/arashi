package dev.duetigh.arashi.scan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Decodes the "ARSH" compact scan format produced by {@link ScanEncoder}. Exists mainly as an
 * internal round-trip check that the format spec is actually symmetric before arashi-render (a
 * separate project with its own from-scratch decoder) depends on it; not required for the scan
 * feature itself to function in-game.
 */
public final class ScanDecoder {
	private ScanDecoder() {
	}

	public static DecodedScan decode(String compactString) {
		byte[] compressed = Base64.getUrlDecoder().decode(compactString);
		return decode(inflate(compressed));
	}

	public static DecodedScan decode(byte[] rawPayload) {
		Cursor cursor = new Cursor(rawPayload);

		byte[] magic = cursor.readBytes(4);
		for (int i = 0; i < 4; i++) {
			if (magic[i] != ScanEncoder.MAGIC[i]) {
				throw new IllegalArgumentException("Not an Arashi scan (bad magic)");
			}
		}

		int version = cursor.readU8();
		if (version != ScanEncoder.FORMAT_VERSION) {
			throw new IllegalArgumentException("Unsupported scan format version: " + version);
		}

		int captureModeId = cursor.readU8();
		CaptureMode captureMode = CaptureMode.fromWireId(captureModeId);
		DecodedScan.DecodedCaptureParams captureParams = readCaptureParams(cursor, captureMode);

		String dimensionId = cursor.readStr();
		int minY = cursor.readI32();
		int maxY = cursor.readI32();
		int columnHeight = maxY - minY;
		int expectedBlocksPerChunk = 16 * 16 * columnHeight;

		int paletteCount = cursor.readVarInt();
		List<String> palette = new ArrayList<>(paletteCount);
		for (int i = 0; i < paletteCount; i++) {
			palette.add(cursor.readStr());
		}

		int chunkCount = cursor.readVarInt();
		List<DecodedScan.DecodedChunk> chunks = new ArrayList<>(chunkCount);

		for (int c = 0; c < chunkCount; c++) {
			int chunkX = cursor.readI32();
			int chunkZ = cursor.readI32();

			int runCount = cursor.readVarInt();
			List<DecodedScan.DecodedRun> runs = new ArrayList<>(runCount);
			int total = 0;

			for (int r = 0; r < runCount; r++) {
				int paletteIndex = cursor.readVarInt();
				int length = cursor.readVarInt();
				runs.add(new DecodedScan.DecodedRun(paletteIndex, length));
				total += length;
			}

			if (total != expectedBlocksPerChunk) {
				throw new IllegalArgumentException("Corrupt/truncated scan data: chunk run lengths summed to "
						+ total + ", expected " + expectedBlocksPerChunk + " (chunk " + chunkX + "," + chunkZ + ")");
			}

			chunks.add(new DecodedScan.DecodedChunk(chunkX, chunkZ, runs));
		}

		return new DecodedScan(dimensionId, minY, maxY, captureMode, captureParams, palette, chunks);
	}

	private static DecodedScan.DecodedCaptureParams readCaptureParams(Cursor cursor, CaptureMode mode) {
		return switch (mode) {
			case EVERYTHING -> null;
			case WHITELIST -> {
				int count = cursor.readVarInt();
				List<String> blocks = new ArrayList<>(count);

				for (int i = 0; i < count; i++) {
					blocks.add(cursor.readStr());
				}

				yield new DecodedScan.WhitelistParams(blocks);
			}
			case ISOLATE -> {
				String seed = cursor.readStr();
				int connectivity = cursor.readU8();
				yield new DecodedScan.IsolateParams(seed, connectivity);
			}
		};
	}

	private static byte[] inflate(byte[] compressed) {
		Inflater inflater = new Inflater();
		inflater.setInput(compressed);
		ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.length * 4);
		byte[] buffer = new byte[8192];

		try {
			while (!inflater.finished()) {
				int n = inflater.inflate(buffer);

				if (n == 0) {
					if (inflater.needsInput() || inflater.needsDictionary()) {
						throw new IllegalArgumentException("Corrupt/truncated scan data (inflate stalled)");
					}

					break;
				}

				out.write(buffer, 0, n);
			}
		} catch (DataFormatException e) {
			throw new IllegalArgumentException("Corrupt/truncated scan data", e);
		} finally {
			inflater.end();
		}

		return out.toByteArray();
	}

	private static final class Cursor {
		private final byte[] data;
		private int pos;

		Cursor(byte[] data) {
			this.data = data;
		}

		byte[] readBytes(int count) {
			byte[] result = java.util.Arrays.copyOfRange(data, pos, pos + count);
			pos += count;
			return result;
		}

		int readU8() {
			return data[pos++] & 0xFF;
		}

		int readI32() {
			int value = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
					| ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
			pos += 4;
			return value;
		}

		int readVarInt() {
			int value = 0;
			int shift = 0;
			int b;

			do {
				b = data[pos++] & 0xFF;
				value |= (b & 0x7F) << shift;
				shift += 7;
			} while ((b & 0x80) != 0);

			return value;
		}

		String readStr() {
			int length = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
			pos += 2;
			String value = new String(data, pos, length, StandardCharsets.UTF_8);
			pos += length;
			return value;
		}
	}
}
