package dev.duetigh.arashirender.view;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.duetigh.arashirender.render.RenderMode;

/** Persists app-wide (not per-scan) settings to {@code ~/.arashi-render/settings.json}. */
public final class SettingsStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = Path.of(System.getProperty("user.home"), ".arashi-render", "settings.json");

	public boolean centerOnLoad;
	public RenderMode renderMode = RenderMode.COLOR;
	public String textureSourcePath;
	public String scanFolderPath;

	public static SettingsStore load() {
		if (!Files.exists(PATH)) {
			return new SettingsStore();
		}

		try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
			SettingsStore loaded = GSON.fromJson(reader, SettingsStore.class);
			return loaded != null ? loaded : new SettingsStore();
		} catch (IOException e) {
			return new SettingsStore();
		}
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());

			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to save settings", e);
		}
	}
}
