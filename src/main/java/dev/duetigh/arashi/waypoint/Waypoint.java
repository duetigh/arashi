package dev.duetigh.arashi.waypoint;

import net.minecraft.core.BlockPos;

/**
 * A single waypoint in a {@link WaypointGroup}. Stores plain x/y/z ints rather than a
 * {@link BlockPos} directly so GSON round-trips it without needing a custom type adapter for
 * Minecraft's internal representation.
 */
public final class Waypoint {
	int x;
	int y;
	int z;
	String type;
	int order;

	Waypoint(BlockPos pos, WaypointType type, int order) {
		this.x = pos.getX();
		this.y = pos.getY();
		this.z = pos.getZ();
		this.type = type.name();
		this.order = order;
	}

	public BlockPos pos() {
		return new BlockPos(x, y, z);
	}

	public WaypointType type() {
		try {
			return WaypointType.valueOf(type);
		} catch (IllegalArgumentException e) {
			return WaypointType.PICKOBULUS;
		}
	}

	public int order() {
		return order;
	}
}
