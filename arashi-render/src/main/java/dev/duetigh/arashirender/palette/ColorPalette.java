package dev.duetigh.arashirender.palette;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-block-id display color (0xRRGGBB), editable in the UI for the current session. Blocks with no
 * explicit entry get a deterministic hash-based color so distinct types are at least visually
 * distinguishable without needing real Minecraft textures.
 */
public final class ColorPalette {
	private static final Map<String, Integer> DEFAULTS = buildDefaults();

	private final Map<String, Integer> overrides = new HashMap<>();

	public int colorFor(String blockId) {
		Integer override = overrides.get(blockId);
		if (override != null) {
			return override;
		}

		Integer known = DEFAULTS.get(blockId);
		return known != null ? known : hashColor(blockId);
	}

	public void setColor(String blockId, int rgb) {
		overrides.put(blockId, rgb & 0xFFFFFF);
	}

	/** True for block ids Arashi hides from the visibility checklist by default (air variants). */
	public static boolean isAirLike(String blockId) {
		return blockId.endsWith(":air") || blockId.endsWith("_air");
	}

	private static int hashColor(String blockId) {
		int hash = blockId.hashCode();
		float hue = ((hash & 0xFFFF) % 360) / 360.0f;
		float saturation = 0.45f + ((hash >>> 16) & 0xFF) / 255.0f * 0.35f;
		float value = 0.55f + ((hash >>> 8) & 0xFF) / 255.0f * 0.35f;
		return hsvToRgb(hue, saturation, value);
	}

	private static int hsvToRgb(float h, float s, float v) {
		int i = (int) (h * 6);
		float f = h * 6 - i;
		float p = v * (1 - s);
		float q = v * (1 - f * s);
		float t = v * (1 - (1 - f) * s);
		float r;
		float g;
		float b;

		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}

		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}

	private static Map<String, Integer> buildDefaults() {
		Map<String, Integer> map = new HashMap<>();
		map.put("minecraft:stone", 0x7F7F7F);
		map.put("minecraft:deepslate", 0x4B4B4E);
		map.put("minecraft:grass_block", 0x5D9C3E);
		map.put("minecraft:dirt", 0x8B5A2B);
		map.put("minecraft:sand", 0xE0D3A0);
		map.put("minecraft:sandstone", 0xD8CB98);
		map.put("minecraft:water", 0x3F76E4);
		map.put("minecraft:lava", 0xE25822);
		map.put("minecraft:bedrock", 0x2B2B2B);
		map.put("minecraft:gravel", 0x8B8880);
		map.put("minecraft:coal_ore", 0x363636);
		map.put("minecraft:iron_ore", 0xC7A87A);
		map.put("minecraft:gold_ore", 0xFCEE4B);
		map.put("minecraft:diamond_ore", 0x5DECE4);
		map.put("minecraft:emerald_ore", 0x3FE089);
		map.put("minecraft:redstone_ore", 0xB3312C);
		map.put("minecraft:lapis_ore", 0x1B4FA0);
		map.put("minecraft:deepslate_diamond_ore", 0x4FADA8);
		map.put("minecraft:deepslate_iron_ore", 0x9E8467);
		map.put("minecraft:deepslate_gold_ore", 0xC7BE55);
		map.put("minecraft:oak_log", 0x6E5432);
		map.put("minecraft:oak_leaves", 0x3E7A2E);
		map.put("minecraft:air", 0xFFFFFF);
		map.put("minecraft:cave_air", 0xFFFFFF);
		map.put("minecraft:void_air", 0xFFFFFF);
		return map;
	}
}
