package dev.duetigh.arashirender.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import dev.duetigh.arashirender.palette.ColorPalette;
import dev.duetigh.arashirender.world.Filters;
import dev.duetigh.arashirender.world.VoxelWorld;

/**
 * Draws a {@link VoxelWorld} as one instanced-cube draw call per visible block type. Culls whole
 * blocks that are fully enclosed by other currently-visible blocks (not per-face culling - a known
 * v1 simplification) so dense solid terrain doesn't cost a draw per buried block.
 */
public final class SceneRenderer {
	private static final String VERTEX_SOURCE = """
			#version 330 core
			layout(location = 0) in vec3 aPos;
			layout(location = 1) in vec3 aNormal;
			layout(location = 2) in vec3 aInstancePos;

			uniform mat4 uView;
			uniform mat4 uProj;

			out vec3 vNormal;

			void main() {
			    vec3 worldPos = aPos + aInstancePos + vec3(0.5);
			    gl_Position = uProj * uView * vec4(worldPos, 1.0);
			    vNormal = aNormal;
			}
			""";

	private static final String FRAGMENT_SOURCE = """
			#version 330 core
			in vec3 vNormal;
			out vec4 fragColor;

			uniform vec3 uColor;
			uniform float uOpacity;

			void main() {
			    vec3 lightDir = normalize(vec3(0.5, 1.0, 0.3));
			    float diffuse = 0.55 + 0.45 * max(dot(normalize(vNormal), lightDir), 0.0);
			    fragColor = vec4(uColor * diffuse, uOpacity);
			}
			""";

	private final CubeGeometry cube = new CubeGeometry();
	private final Shader shader = new Shader(VERTEX_SOURCE, FRAGMENT_SOURCE);
	private final Map<Integer, InstancedBatch> batches = new HashMap<>();

	private static final int[][] NEIGHBORS_6 = {
			{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
	};

	/** Recomputes which blocks are drawn, grouped by type. Call whenever filters or the loaded scan change - not per frame. */
	public void rebuild(VoxelWorld world, Filters filters, ColorPalette palette) {
		Map<Integer, List<float[]>> positionsByType = new HashMap<>();

		for (Map.Entry<Long, Integer> entry : world.cells().entrySet()) {
			long key = entry.getKey();
			int paletteIndex = entry.getValue();
			int x = VoxelWorld.unpackX(key);
			int y = VoxelWorld.unpackY(key);
			int z = VoxelWorld.unpackZ(key);

			if (!filters.isVisible(key, x, y, z, paletteIndex)) {
				continue;
			}

			boolean enclosed = true;

			for (int[] offset : NEIGHBORS_6) {
				int nx = x + offset[0];
				int ny = y + offset[1];
				int nz = z + offset[2];
				int nIndex = world.get(nx, ny, nz);
				boolean neighborVisible = nIndex != -1 && filters.isVisible(VoxelWorld.pack(nx, ny, nz), nx, ny, nz, nIndex);

				if (!neighborVisible) {
					enclosed = false;
					break;
				}
			}

			if (enclosed) {
				continue;
			}

			positionsByType.computeIfAbsent(paletteIndex, i -> new ArrayList<>()).add(new float[]{x, y, z});
		}

		for (InstancedBatch stale : batches.values()) {
			stale.destroy();
		}

		batches.clear();

		for (Map.Entry<Integer, List<float[]>> entry : positionsByType.entrySet()) {
			InstancedBatch batch = new InstancedBatch(cube);
			batch.setColor(palette.colorFor(world.palette()[entry.getKey()]));
			batch.upload(entry.getValue());
			batches.put(entry.getKey(), batch);
		}
	}

	public void render(Matrix4f view, Matrix4f proj, float opacity) {
		shader.use();
		shader.setMatrix4("uView", view);
		shader.setMatrix4("uProj", proj);
		shader.setFloat("uOpacity", opacity);

		for (InstancedBatch batch : batches.values()) {
			float[] color = batch.color();
			shader.setVec3("uColor", color[0], color[1], color[2]);
			batch.draw(cube);
		}
	}

	public void destroy() {
		for (InstancedBatch batch : batches.values()) {
			batch.destroy();
		}

		cube.destroy();
		shader.destroy();
	}
}
