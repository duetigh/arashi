package dev.duetigh.arashirender.world;

import org.joml.Vector3f;

/** Amanatides-Woo voxel DDA raycast against a {@link VoxelWorld}, respecting the active {@link Filters}. */
public final class Raycaster {
	public record Hit(int x, int y, int z, int paletteIndex) {
	}

	private Raycaster() {
	}

	public static Hit cast(VoxelWorld world, Filters filters, Vector3f origin, Vector3f direction, float maxDistance) {
		int x = (int) Math.floor(origin.x);
		int y = (int) Math.floor(origin.y);
		int z = (int) Math.floor(origin.z);

		int stepX = Integer.signum(Float.compare(direction.x, 0));
		int stepY = Integer.signum(Float.compare(direction.y, 0));
		int stepZ = Integer.signum(Float.compare(direction.z, 0));

		float tDeltaX = direction.x != 0 ? Math.abs(1.0f / direction.x) : Float.POSITIVE_INFINITY;
		float tDeltaY = direction.y != 0 ? Math.abs(1.0f / direction.y) : Float.POSITIVE_INFINITY;
		float tDeltaZ = direction.z != 0 ? Math.abs(1.0f / direction.z) : Float.POSITIVE_INFINITY;

		float tMaxX = tDeltaX == Float.POSITIVE_INFINITY ? Float.POSITIVE_INFINITY : boundaryDistance(origin.x, x, stepX) * tDeltaX;
		float tMaxY = tDeltaY == Float.POSITIVE_INFINITY ? Float.POSITIVE_INFINITY : boundaryDistance(origin.y, y, stepY) * tDeltaY;
		float tMaxZ = tDeltaZ == Float.POSITIVE_INFINITY ? Float.POSITIVE_INFINITY : boundaryDistance(origin.z, z, stepZ) * tDeltaZ;

		float traveled = 0;

		while (traveled <= maxDistance) {
			int paletteIndex = world.get(x, y, z);

			if (paletteIndex != -1) {
				long key = VoxelWorld.pack(x, y, z);

				if (filters.isVisible(key, x, y, z, paletteIndex)) {
					return new Hit(x, y, z, paletteIndex);
				}
			}

			if (tMaxX < tMaxY) {
				if (tMaxX < tMaxZ) {
					x += stepX;
					traveled = tMaxX;
					tMaxX += tDeltaX;
				} else {
					z += stepZ;
					traveled = tMaxZ;
					tMaxZ += tDeltaZ;
				}
			} else {
				if (tMaxY < tMaxZ) {
					y += stepY;
					traveled = tMaxY;
					tMaxY += tDeltaY;
				} else {
					z += stepZ;
					traveled = tMaxZ;
					tMaxZ += tDeltaZ;
				}
			}
		}

		return null;
	}

	private static float boundaryDistance(float originCoord, int cell, int step) {
		if (step > 0) {
			return (cell + 1) - originCoord;
		} else if (step < 0) {
			return originCoord - cell;
		}

		return Float.POSITIVE_INFINITY;
	}
}
