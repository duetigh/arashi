package dev.duetigh.arashi.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
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

	/** Outline hue, 0.0-1.0, at full saturation/brightness. */
	public float outlineHue() {
		return data.outlineHue;
	}

	public void setOutlineHue(float hue) {
		data.outlineHue = hue;
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
		float outlineHue = 0.0f;
		float outlineWidth = 2.0f;
		float fillOpacity = 0.45f;
		float outlineOpacity = 1.0f;
		boolean chatCoordsEnabled = false;
	}
}
