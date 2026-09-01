package dev.duetigh.arashi.waypoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;

/** A named, ordered collection of waypoints. */
public final class WaypointGroup {
	String id;
	String name;
	List<Waypoint> waypoints = new ArrayList<>();

	WaypointGroup(String id, String name) {
		this.id = id;
		this.name = name;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/** Waypoints in placement order. */
	public List<Waypoint> waypoints() {
		return waypoints.stream().sorted(Comparator.comparingInt(Waypoint::order)).toList();
	}

	/** Adds a waypoint with the next order number in this group (max existing order + 1, starting at 1). */
	Waypoint add(BlockPos pos, WaypointType type) {
		int nextOrder = waypoints.stream().mapToInt(Waypoint::order).max().orElse(0) + 1;
		Waypoint waypoint = new Waypoint(pos, type, nextOrder);
		waypoints.add(waypoint);
		return waypoint;
	}

	void remove(int order) {
		waypoints.removeIf(w -> w.order() == order);
	}
}
