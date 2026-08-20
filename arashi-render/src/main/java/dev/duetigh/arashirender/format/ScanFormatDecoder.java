package dev.duetigh.arashirender.format;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Decodes the Arashi "ARSH" compact scan format (magic + varint/RLE section runs, zlib-deflated, Base64 URL-safe no-pad). */
public final class ScanFormatDecoder {
	private static final byte[] MAGIC = {'A', 'R', 'S', 'H'};
	private static final int SUPPORTED_VERSION = 1;

	private ScanFormatDecoder() {
	}

	public static DecodedScan decode(String compactString) {
		byte[] compressed = Base64.getUrlDecoder().decode(compactString.strip());
		return decode(inflate(compressed));
	}

	public static DecodedScan decode(byte[] rawPayload) {
		Cursor cursor = new Cursor(rawPayload);

		byte[] magic = cursor.readBytes(4);
		if (!Arrays.equals(magic, MAGIC)) {
			throw new IllegalArgumentException("Not an Arashi scan string (bad magic header)");
		}

		int version = cursor.readU8();
		if (version != SUPPORTED_VERSION) {
			throw new IllegalArgumentException("Unsupported scan format version: " + version
					+ " (this build of arashi-render only supports version " + SUPPORTED_VERSION + ")");
		}

		String dimensionId = cursor.readStr();
		int minY = cursor.readI32();
		int maxY = cursor.readI32();
		int sectionCount = (maxY - minY) / 16;

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
			List<List<DecodedScan.Run>> sections = new ArrayList<>(sectionCount);

			for (int s = 0; s < sectionCount; s++) {
				int runCount = cursor.readVarInt();
				List<DecodedScan.Run> runs = new ArrayList<>(runCount);
				int total = 0;

				for (int r = 0; r < runCount; r++) {
					int paletteIndex = cursor.readVarInt();
					int length = cursor.readVarInt();
					runs.add(new DecodedScan.Run(paletteIndex, length));
					total += length;
				}

				if (total != 4096) {
					throw new IllegalArgumentException("Corrupt/truncated scan data: section run lengths summed to "
							+ total + ", expected 4096 (chunk " + chunkX + "," + chunkZ + " section " + s + ")");
				}

				sections.add(runs);
			}

			chunks.add(new DecodedScan.DecodedChunk(chunkX, chunkZ, sections));
		}

		return new DecodedScan(dimensionId, minY, maxY, palette, chunks);
	}

	private static byte[] inflate(byte[] compressed) {
		Inflater inflater = new Inflater();
		inflater.setInput(compressed);
		ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, compressed.length * 4));
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
			byte[] result = Arrays.copyOfRange(data, pos, pos + count);
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
