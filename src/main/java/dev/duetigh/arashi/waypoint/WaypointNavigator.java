package dev.duetigh.arashi.waypoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.BlockPos;

import dev.duetigh.arashi.config.ArashiConfig;

/**
 * Coleweight-style runtime navigation through the active waypoint group: advances to the next
 * waypoint once the player stands directly on top of the current one (its position plus one Y
 * level), sits idle once there's no active group, and loops back to the first waypoint after the
 * last one is reached rather than stopping.
 */
public final class WaypointNavigator {
	private final ArashiConfig config;
	private final WaypointStore store;

	private String activeGroupId;
	private List<Waypoint> activeWaypoints = List.of();
	private int currentIndex;

	public WaypointNavigator(ArashiConfig config, WaypointStore store) {
		this.config = config;
		this.store = store;
	}

	public void tick(BlockPos playerBlockPos) {
		String configuredGroupId = config.activeWaypointGroupId();

		if (!Objects.equals(configuredGroupId, activeGroupId)) {
			activeGroupId = configuredGroupId;
			activeWaypoints = configuredGroupId != null
					? store.get(configuredGroupId).map(WaypointGroup::waypoints).orElse(List.of())
					: List.of();
			currentIndex = 0;
		}

		if (activeWaypoints.isEmpty()) {
			return;
		}

		Waypoint current = activeWaypoints.get(currentIndex);

		if (playerBlockPos.equals(current.pos().above())) {
			currentIndex = (currentIndex + 1) % activeWaypoints.size();
		}
	}

	public Optional<Waypoint> current() {
		return at(currentIndex);
	}

	public Optional<Waypoint> next() {
		return at(currentIndex + 1);
	}

	public Optional<Waypoint> nextNext() {
		return at(currentIndex + 2);
	}

	/** Wraps around the end of the group, so the route loops back to the first waypoint instead of ending. */
	private Optional<Waypoint> at(int offsetIndex) {
		return activeWaypoints.isEmpty() ? Optional.empty() : Optional.of(activeWaypoints.get(offsetIndex % activeWaypoints.size()));
	}
}
