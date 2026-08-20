package dev.duetigh.arashirender.render;

import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL33.*;

/** One draw call's worth of same-block-type cubes: a VAO sharing the unit-cube VBO plus a per-instance position VBO. */
public final class InstancedBatch {
	private final int vao;
	private final int instanceVbo;
	private int instanceCount;
	private float[] color = {1, 1, 1};

	public InstancedBatch(CubeGeometry cube) {
		vao = glGenVertexArrays();
		glBindVertexArray(vao);

		glBindBuffer(GL_ARRAY_BUFFER, cube.vbo());
		int stride = 6 * Float.BYTES;
		glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
		glEnableVertexAttribArray(1);

		instanceVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
		glVertexAttribPointer(2, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
		glEnableVertexAttribArray(2);
		glVertexAttribDivisor(2, 1);

		glBindVertexArray(0);
	}

	public void setColor(int rgb) {
		color[0] = ((rgb >> 16) & 0xFF) / 255.0f;
		color[1] = ((rgb >> 8) & 0xFF) / 255.0f;
		color[2] = (rgb & 0xFF) / 255.0f;
	}

	public float[] color() {
		return color;
	}

	/** Uploads block-corner world positions (already offset to the -X/-Y/-Z corner of each cube) for this batch. */
	public void upload(List<float[]> positions) {
		instanceCount = positions.size();

		if (instanceCount == 0) {
			return;
		}

		FloatBuffer buffer = MemoryUtil.memAllocFloat(instanceCount * 3);

		for (float[] pos : positions) {
			buffer.put(pos[0]).put(pos[1]).put(pos[2]);
		}

		buffer.flip();
		glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
		glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
		MemoryUtil.memFree(buffer);
	}

	public void draw(CubeGeometry cube) {
		if (instanceCount == 0) {
			return;
		}

		glBindVertexArray(vao);
		glDrawArraysInstanced(GL_TRIANGLES, 0, cube.vertexCount(), instanceCount);
	}

	public void destroy() {
		glDeleteVertexArrays(vao);
		glDeleteBuffers(instanceVbo);
	}
}
