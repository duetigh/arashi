package dev.duetigh.arashirender.render;

import java.nio.FloatBuffer;

import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL33.*;

/** Shared unit-cube vertex buffer (position + normal + UV, 36 non-indexed vertices) reused by every {@link InstancedBatch}. */
public final class CubeGeometry {
	// 6 faces * 2 triangles * 3 vertices, each vertex = position(3) + normal(3) + uv(2).
	private static final float[] VERTICES = buildCube();

	private final int vbo;

	public CubeGeometry() {
		vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		FloatBuffer buffer = MemoryUtil.memAllocFloat(VERTICES.length);
		buffer.put(VERTICES).flip();
		glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
		MemoryUtil.memFree(buffer);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	public int vbo() {
		return vbo;
	}

	public int vertexCount() {
		return VERTICES.length / 8;
	}

	public void destroy() {
		glDeleteBuffers(vbo);
	}

	private static float[] buildCube() {
		// Explicit corner list per face, wound counter-clockwise when viewed from outside.
		float[][][] faceCorners = {
				{{0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}, {0.5f, 0.5f, -0.5f}}, // +X
				{{-0.5f, -0.5f, 0.5f}, {-0.5f, -0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, 0.5f}}, // -X
				{{-0.5f, 0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f}}, // +Y
				{{-0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, -0.5f}, {-0.5f, -0.5f, -0.5f}}, // -Y
				{{0.5f, -0.5f, 0.5f}, {-0.5f, -0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}}, // +Z
				{{-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f}}, // -Z
		};

		float[][] normals = {
				{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
		};

		// Same (0,0)-(1,0)-(1,1)-(0,1) planar UV square on every face - one texture per block, not a
		// full per-face atlas, so exact orientation per face doesn't matter for this v1.
		float[][] faceUv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

		float[] out = new float[6 * 6 * 8];
		int i = 0;

		for (int f = 0; f < 6; f++) {
			float[][] corners = faceCorners[f];
			float[] n = normals[f];
			int[][] tris = {{0, 1, 2}, {0, 2, 3}};

			for (int[] tri : tris) {
				for (int c : tri) {
					out[i++] = corners[c][0];
					out[i++] = corners[c][1];
					out[i++] = corners[c][2];
					out[i++] = n[0];
					out[i++] = n[1];
					out[i++] = n[2];
					out[i++] = faceUv[c][0];
					out[i++] = faceUv[c][1];
				}
			}
		}

		return out;
	}
}
