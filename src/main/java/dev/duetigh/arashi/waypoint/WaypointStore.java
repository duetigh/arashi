package dev.duetigh.arashi.waypoint;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

/**
 * Persists waypoint groups to {@code config/arashi/waypoints/}: an {@code index.json} listing
 * id/name/count (so the manager screen never needs to load every group's full waypoint list) plus
 * one {@code <id>.json} per group holding its ordered waypoints. Mirrors {@code scan/ScanStore}'s
 * index-plus-per-item-file layout.
 */
public final class WaypointStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path dir;
	private final Path indexPath;
	private final List<GroupEntry> entries;

	private WaypointStore(Path dir, List<GroupEntry> entries) {
		this.dir = dir;
		this.indexPath = dir.resolve("index.json");
		this.entries = entries;
	}

	public static WaypointStore load() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("arashi").resolve("waypoints");
		Path indexPath = dir.resolve("index.json");

		if (!Files.exists(indexPath)) {
			return new WaypointStore(dir, new ArrayList<>());
		}

		try (Reader reader = Files.newBufferedReader(indexPath, StandardCharsets.UTF_8)) {
			List<GroupEntry> loaded = GSON.fromJson(reader, new TypeToken<List<GroupEntry>>() { }.getType());
			return new WaypointStore(dir, loaded != null ? loaded : new ArrayList<>());
		} catch (IOException e) {
			return new WaypointStore(dir, new ArrayList<>());
		}
	}

	/** Name-sorted list of saved group metadata. */
	public List<GroupEntry> list() {
		return entries.stream().sorted(Comparator.comparing(GroupEntry::name, String.CASE_INSENSITIVE_ORDER)).toList();
	}

	public WaypointGroup createGroup(String name) {
		String id = UUID.randomUUID().toString();
		WaypointGroup group = new WaypointGroup(id, name);
		persistGroup(group);
		entries.add(new GroupEntry(id, name, 0));
		persistIndex();
		return group;
	}

	public Optional<WaypointGroup> get(String id) {
		if (id == null) {
			return Optional.empty();
		}

		Path path = groupPath(id);

		if (!Files.exists(path)) {
			return Optional.empty();
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return Optional.ofNullable(GSON.fromJson(reader, WaypointGroup.class));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	public Waypoint addWaypoint(String groupId, BlockPos pos, WaypointType type) {
		WaypointGroup group = get(groupId).orElseThrow(() -> new IllegalArgumentException("Unknown waypoint group: " + groupId));
		Waypoint waypoint = group.add(pos, type);
		persistGroup(group);
		updateCount(groupId, group.waypoints().size());
		return waypoint;
	}

	public void removeWaypoint(String groupId, int order) {
		get(groupId).ifPresent(group -> {
			group.remove(order);
			persistGroup(group);
			updateCount(groupId, group.waypoints().size());
		});
	}

	public void rename(String id, String newName) {
		findEntry(id).ifPresent(entry -> {
			entry.name = newName;
			persistIndex();
		});

		get(id).ifPresent(group -> {
			group.setName(newName);
			persistGroup(group);
		});
	}

	/** Encodes a group's name and waypoints as a self-contained string others can paste into "import". */
	public Optional<String> export(String id) {
		return get(id).map(group -> Base64.getEncoder().encodeToString(GSON.toJson(group).getBytes(StandardCharsets.UTF_8)));
	}

	/** Decodes a string from {@link #export} into a brand-new local group (its own id, never colliding with the source's). */
	public WaypointGroup importGroup(String encoded) {
		String json;

		try {
			json = new String(Base64.getDecoder().decode(encoded.strip()), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("That doesn't look like exported waypoint data.", e);
		}

		WaypointGroup decoded = GSON.fromJson(json, WaypointGroup.class);

		if (decoded == null || decoded.name() == null || decoded.name().isBlank()) {
			throw new IllegalArgumentException("That doesn't look like exported waypoint data.");
		}

		WaypointGroup group = createGroup(decoded.name());

		for (Waypoint waypoint : decoded.waypoints()) {
			addWaypoint(group.id(), waypoint.pos(), waypoint.type());
		}

		return group;
	}

	public void delete(String id) {
		findEntry(id).ifPresent(entry -> {
			entries.remove(entry);
			persistIndex();

			try {
				Files.deleteIfExists(groupPath(id));
			} catch (IOException e) {
				throw new RuntimeException("Failed to delete waypoint group file " + groupPath(id), e);
			}
		});
	}

	private void updateCount(String id, int count) {
		findEntry(id).ifPresent(entry -> {
			entry.waypointCount = count;
			persistIndex();
		});
	}

	private Optional<GroupEntry> findEntry(String id) {
		return entries.stream().filter(e -> e.id().equals(id)).findFirst();
	}

	private Path groupPath(String id) {
		return dir.resolve(id + ".json");
	}

	private void persistGroup(WaypointGroup group) {
		try {
			Files.createDirectories(dir);

			try (Writer writer = Files.newBufferedWriter(groupPath(group.id()), StandardCharsets.UTF_8)) {
				GSON.toJson(group, writer);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to save waypoint group to " + groupPath(group.id()), e);
		}
	}

	private void persistIndex() {
		try {
			Files.createDirectories(dir);

			try (Writer writer = Files.newBufferedWriter(indexPath, StandardCharsets.UTF_8)) {
				GSON.toJson(entries, writer);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to save waypoint index to " + indexPath, e);
		}
	}

	/** Index metadata for one saved waypoint group. */
	public static final class GroupEntry {
		String id;
		String name;
		int waypointCount;

		GroupEntry(String id, String name, int waypointCount) {
			this.id = id;
			this.name = name;
			this.waypointCount = waypointCount;
		}

		public String id() {
			return id;
		}

		public String name() {
			return name;
		}

		public int waypointCount() {
			return waypointCount;
		}
	}
}
