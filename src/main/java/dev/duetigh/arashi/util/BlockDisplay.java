package dev.duetigh.arashi.util;

import net.minecraft.resources.Identifier;

/** Formats block identifiers for display, dropping the {@code minecraft:} namespace since it's implied. */
public final class BlockDisplay {
	private BlockDisplay() {
	}

	public static String shortName(Identifier id) {
		return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
	}
}
