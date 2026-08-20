package dev.duetigh.arashirender.view;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/** Persists saved views to {@code ~/.arashi-render/views/<scanId>.json}, one file per distinct scan (see {@link ScanIdentity}). */
public final class ViewStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = Path.of(System.getProperty("user.home"), ".arashi-render", "views");

	private final String scanId;
	private final String scanName;
	private final List<SavedView> views;

	private ViewStore(String scanId, String scanName, List<SavedView> views) {
		this.scanId = scanId;
		this.scanName = scanName;
		this.views = views;
	}

	public static ViewStore loadFor(String scanId, String scanName) {
		Path path = DIR.resolve(scanId + ".json");

		if (!Files.exists(path)) {
			return new ViewStore(scanId, scanName, new ArrayList<>());
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Data data = GSON.fromJson(reader, Data.class);
			return new ViewStore(scanId, scanName, data != null && data.views != null ? data.views : new ArrayList<>());
		} catch (IOException e) {
			return new ViewStore(scanId, scanName, new ArrayList<>());
		}
	}

	public List<SavedView> views() {
		return views;
	}

	public void save(SavedView view) {
		views.removeIf(existing -> existing.name.equals(view.name));
		views.add(view);
		persist();
	}

	public void delete(String name) {
		views.removeIf(existing -> existing.name.equals(name));
		persist();
	}

	private void persist() {
		try {
			Files.createDirectories(DIR);
			Data data = new Data();
			data.scanId = scanId;
			data.scanName = scanName;
			data.views = views;

			try (Writer writer = Files.newBufferedWriter(DIR.resolve(scanId + ".json"), StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to save views for scan " + scanId, e);
		}
	}

	private static final class Data {
		String scanId;
		String scanName;
		List<SavedView> views;
	}
}
