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
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

/** Persists the set of tracked block ids to {@code config/arashi.json}. */
public final class ArashiConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("arashi.json");

	private final Set<String> trackedBlockIds;

	private ArashiConfig(Set<String> trackedBlockIds) {
		this.trackedBlockIds = trackedBlockIds;
	}

	public static ArashiConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				Set<String> loaded = GSON.fromJson(reader, new TypeToken<LinkedHashSet<String>>() { }.getType());
				return new ArashiConfig(loaded != null ? loaded : new LinkedHashSet<>());
			} catch (IOException e) {
				return new ArashiConfig(new LinkedHashSet<>());
			}
		}

		return new ArashiConfig(new LinkedHashSet<>());
	}

	public Set<String> trackedBlockIds() {
		return trackedBlockIds;
	}

	public boolean toggle(String blockId) {
		boolean added = trackedBlockIds.contains(blockId) ? !trackedBlockIds.remove(blockId) : trackedBlockIds.add(blockId);
		save();
		return added;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());

			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(trackedBlockIds, writer);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to save Arashi config to " + PATH, e);
		}
	}
}
