package dev.duetigh.arashirender.render;

import java.nio.FloatBuffer;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL33.*;

/**
 * Draws a wireframe box around a single block cell - used for the live hover preview and the
 * click-selected block. Slightly larger than a unit cube so it doesn't z-fight with the solid faces
 * drawn by {@link SceneRenderer}. Depth-tested against the scene (so it's hidden behind solid blocks
 * in front of it) but doesn't write depth, so it never occludes anything drawn after it.
 */
public final class OutlineRenderer {
	private static final float SCALE = 1.02f;

	private static final String VERTEX_SOURCE = """
			#version 330 core
			layout(location = 0) in vec3 aPos;

			uniform mat4 uView;
			uniform mat4 uProj;
			uniform vec3 uPosition;

			void main() {
			    vec3 worldPos = aPos + uPosition + vec3(0.5);
			    gl_Position = uProj * uView * vec4(worldPos, 1.0);
			}
			""";

	private static final String FRAGMENT_SOURCE = """
			#version 330 core
			out vec4 fragColor;

			uniform vec3 uColor;
			uniform float uOpacity;

			void main() {
			    fragColor = vec4(uColor, uOpacity);
			}
			""";

	private final Shader shader = new Shader(VERTEX_SOURCE, FRAGMENT_SOURCE);
	private final int vao;
	private final int vbo;

	public OutlineRenderer() {
		float[] vertices = buildEdges();

		vao = glGenVertexArrays();
		vbo = glGenBuffers();

		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, vbo);

		FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
		buffer.put(vertices).flip();
		glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
		MemoryUtil.memFree(buffer);

		glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
		glEnableVertexAttribArray(0);
		glBindVertexArray(0);
	}

	/** Draws a box outline at the given block's floor coordinates (x, y, z). Call between scene draws. */
	public void draw(Matrix4f view, Matrix4f proj, int x, int y, int z, float r, float g, float b, float opacity) {
		glDepthMask(false);

		shader.use();
		shader.setMatrix4("uView", view);
		shader.setMatrix4("uProj", proj);
		shader.setVec3("uPosition", x, y, z);
		shader.setVec3("uColor", r, g, b);
		shader.setFloat("uOpacity", opacity);

		glBindVertexArray(vao);
		glLineWidth(2f);
		glDrawArrays(GL_LINES, 0, 24);
		glBindVertexArray(0);

		glDepthMask(true);
	}

	public void destroy() {
		glDeleteVertexArrays(vao);
		glDeleteBuffers(vbo);
		shader.destroy();
	}

	private static float[] buildEdges() {
		float h = 0.5f * SCALE;
		float[][] corners = {
				{-h, -h, -h}, {h, -h, -h}, {h, -h, h}, {-h, -h, h}, // bottom face, CCW
				{-h, h, -h}, {h, h, -h}, {h, h, h}, {-h, h, h}, // top face, CCW
		};

		int[][] edges = {
				{0, 1}, {1, 2}, {2, 3}, {3, 0}, // bottom ring
				{4, 5}, {5, 6}, {6, 7}, {7, 4}, // top ring
				{0, 4}, {1, 5}, {2, 6}, {3, 7}, // verticals
		};

		float[] out = new float[edges.length * 2 * 3];
		int i = 0;

		for (int[] edge : edges) {
			for (int corner : edge) {
				out[i++] = corners[corner][0];
				out[i++] = corners[corner][1];
				out[i++] = corners[corner][2];
			}
		}

		return out;
	}
}
