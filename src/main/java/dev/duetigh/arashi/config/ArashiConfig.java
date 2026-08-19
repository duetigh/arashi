package dev.duetigh.arashi.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

/** Persists tracked block ids and ESP display settings to {@code config/arashi.json}. */
public final class ArashiConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("arashi.json");

	private final Data data;

	private ArashiConfig(Data data) {
		this.data = data;
	}

	public static ArashiConfig load() {
		if (!Files.exists(PATH)) {
			return new ArashiConfig(new Data());
		}

		try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);

			// Older versions of this file were a bare JSON array of tracked block ids.
			if (root.isJsonArray()) {
				Data data = new Data();
				Set<String> legacy = GSON.fromJson(root, new TypeToken<LinkedHashSet<String>>() { }.getType());
				data.trackedBlockIds = legacy != null ? legacy : new LinkedHashSet<>();
				return new ArashiConfig(data);
			}

			Data data = GSON.fromJson(root, Data.class);
			return new ArashiConfig(data != null ? data : new Data());
		} catch (IOException | JsonParseException e) {
			return new ArashiConfig(new Data());
		}
	}

	public Set<String> trackedBlockIds() {
		return data.trackedBlockIds;
	}

	public boolean toggle(String blockId) {
		boolean added = data.trackedBlockIds.contains(blockId) ? !data.trackedBlockIds.remove(blockId) : data.trackedBlockIds.add(blockId);
		save();
		return added;
	}

	public EspMode espMode() {
		try {
			return EspMode.valueOf(data.espMode);
		} catch (IllegalArgumentException e) {
			return EspMode.BOTH;
		}
	}

	public void setEspMode(EspMode mode) {
		data.espMode = mode.name();
	}

	/** Outline color for a tracked block, packed as 0xRRGGBB. Falls back to the default color if the block has no override. */
	public int outlineColorFor(String blockId) {
		BlockColor override = data.blockColors.get(blockId);
		return override != null ? override.outlineColor : data.outlineColor;
	}

	public void setOutlineColorFor(String blockId, int rgb) {
		blockColorFor(blockId).outlineColor = rgb & 0xFFFFFF;
	}

	/** Fill color for a tracked block, packed as 0xRRGGBB. Falls back to the default color if the block has no override. */
	public int fillColorFor(String blockId) {
		BlockColor override = data.blockColors.get(blockId);
		return override != null ? override.fillColor : data.fillColor;
	}

	public void setFillColorFor(String blockId, int rgb) {
		blockColorFor(blockId).fillColor = rgb & 0xFFFFFF;
	}

	private BlockColor blockColorFor(String blockId) {
		return data.blockColors.computeIfAbsent(blockId, id -> new BlockColor(data.outlineColor, data.fillColor));
	}

	/** Outline width in pixels. */
	public float outlineWidth() {
		return data.outlineWidth;
	}

	public void setOutlineWidth(float width) {
		data.outlineWidth = width;
	}

	/** Fill opacity, 0.0-1.0. */
	public float fillOpacity() {
		return data.fillOpacity;
	}

	public void setFillOpacity(float opacity) {
		data.fillOpacity = opacity;
	}

	/** Outline opacity, 0.0-1.0. */
	public float outlineOpacity() {
		return data.outlineOpacity;
	}

	public void setOutlineOpacity(float opacity) {
		data.outlineOpacity = opacity;
	}

	public boolean chatCoordsEnabled() {
		return data.chatCoordsEnabled;
	}

	public void setChatCoordsEnabled(boolean enabled) {
		data.chatCoordsEnabled = enabled;
	}

	public boolean espEnabled() {
		return data.espEnabled;
	}

	public void setEspEnabled(boolean enabled) {
		data.espEnabled = enabled;
	}

	/** Whether ESP scanning is restricted to Crystal Hollows while connected to Hypixel. */
	public boolean restrictToCrystalHollows() {
		return data.restrictToCrystalHollows;
	}

	public void setRestrictToCrystalHollows(boolean enabled) {
		data.restrictToCrystalHollows = enabled;
	}

	public boolean lobbySearchedTextEnabled() {
		return data.lobbySearchedTextEnabled;
	}

	public void setLobbySearchedTextEnabled(boolean enabled) {
		data.lobbySearchedTextEnabled = enabled;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());

			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to save Arashi config to " + PATH, e);
		}
	}

	private static final class Data {
		Set<String> trackedBlockIds = new LinkedHashSet<>();
		String espMode = EspMode.BOTH.name();
		int outlineColor = 0xFF0000;
		int fillColor = 0xFF3333;
		float outlineWidth = 2.0f;
		float fillOpacity = 0.45f;
		float outlineOpacity = 1.0f;
		boolean chatCoordsEnabled = false;
		boolean espEnabled = true;
		boolean restrictToCrystalHollows = true;
		boolean lobbySearchedTextEnabled = true;
		Map<String, BlockColor> blockColors = new LinkedHashMap<>();
	}

	/** Per-block outline/fill color override, packed as 0xRRGGBB. */
	private static final class BlockColor {
		int outlineColor;
		int fillColor;

		BlockColor(int outlineColor, int fillColor) {
			this.outlineColor = outlineColor;
			this.fillColor = fillColor;
		}
	}
}
