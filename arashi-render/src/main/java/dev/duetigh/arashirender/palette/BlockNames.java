package dev.duetigh.arashirender.palette;

/** Friendly display names for block registry ids, e.g. {@code minecraft:deepslate_diamond_ore} -> {@code Deepslate Diamond Ore}. */
public final class BlockNames {
	private BlockNames() {
	}

	public static String friendly(String blockId) {
		String path = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
		String[] parts = path.split("_");
		StringBuilder result = new StringBuilder();

		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}

			if (!result.isEmpty()) {
				result.append(' ');
			}

			result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}

		return result.toString();
	}
}
