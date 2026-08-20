package dev.duetigh.arashirender.texture;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves a block id to a single representative texture path under {@code assets/minecraft/textures/}
 * inside a Minecraft jar. Arashi renders every block as a plain cube (no per-face UV atlas), so this
 * picks one texture per block rather than mapping all six faces - a small override table for common
 * multi-texture blocks (logs, grass, etc.), falling back to {@code block/<path>.png} for everything
 * else. Blocks with no matching jar entry fall back to the flat palette color at render time.
 */
public final class BlockTextureMap {
	private static final Map<String, String> OVERRIDES = buildOverrides();

	private BlockTextureMap() {
	}

	/** Texture path (relative to {@code assets/minecraft/textures/}, including the {@code .png}) for a block id. */
	public static String texturePathFor(String blockId) {
		String path = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
		String override = OVERRIDES.get(path);
		return override != null ? "block/" + override + ".png" : "block/" + path + ".png";
	}

	private static Map<String, String> buildOverrides() {
		Map<String, String> map = new HashMap<>();
		map.put("grass_block", "grass_block_top");
		map.put("mycelium", "mycelium_top");
		map.put("podzol", "podzol_top");
		map.put("dirt_path", "dirt_path_top");
		map.put("farmland", "farmland");
		map.put("oak_log", "oak_log_top");
		map.put("spruce_log", "spruce_log_top");
		map.put("birch_log", "birch_log_top");
		map.put("jungle_log", "jungle_log_top");
		map.put("acacia_log", "acacia_log_top");
		map.put("dark_oak_log", "dark_oak_log_top");
		map.put("mangrove_log", "mangrove_log_top");
		map.put("cherry_log", "cherry_log_top");
		map.put("crimson_stem", "crimson_stem_top");
		map.put("warped_stem", "warped_stem_top");
		map.put("stripped_oak_log", "stripped_oak_log_top");
		map.put("stripped_spruce_log", "stripped_spruce_log_top");
		map.put("stripped_birch_log", "stripped_birch_log_top");
		map.put("crafting_table", "crafting_table_top");
		map.put("furnace", "furnace_front");
		map.put("blast_furnace", "blast_furnace_front");
		map.put("smoker", "smoker_front");
		map.put("tnt", "tnt_top");
		map.put("bookshelf", "bookshelf");
		map.put("cake", "cake_top");
		map.put("melon", "melon_top");
		map.put("pumpkin", "pumpkin_top");
		map.put("carved_pumpkin", "carved_pumpkin");
		map.put("jack_o_lantern", "jack_o_lantern");
		map.put("hay_block", "hay_block_top");
		map.put("bone_block", "bone_block_top");
		map.put("bee_nest", "bee_nest_top");
		map.put("beehive", "beehive_end");
		map.put("composter", "composter_top");
		map.put("cauldron", "cauldron_top");
		map.put("end_portal_frame", "end_portal_frame_top");
		map.put("respawn_anchor", "respawn_anchor_top_off");
		return map;
	}
}
