package dev.duetigh.arashirender.render;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/** Minimal GLSL program wrapper: compile, link, and set a handful of uniforms. */
public final class Shader {
	private final int program;

	public Shader(String vertexSource, String fragmentSource) {
		int vertex = compile(GL_VERTEX_SHADER, vertexSource);
		int fragment = compile(GL_FRAGMENT_SHADER, fragmentSource);

		program = glCreateProgram();
		glAttachShader(program, vertex);
		glAttachShader(program, fragment);
		glLinkProgram(program);

		if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
			throw new IllegalStateException("Shader link failed: " + glGetProgramInfoLog(program));
		}

		glDeleteShader(vertex);
		glDeleteShader(fragment);
	}

	private static int compile(int type, String source) {
		int shader = glCreateShader(type);
		glShaderSource(shader, source);
		glCompileShader(shader);

		if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
			throw new IllegalStateException("Shader compile failed: " + glGetShaderInfoLog(shader));
		}

		return shader;
	}

	public void use() {
		glUseProgram(program);
	}

	public void setMatrix4(String name, Matrix4f matrix) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer buffer = stack.mallocFloat(16);
			matrix.get(buffer);
			glUniformMatrix4fv(glGetUniformLocation(program, name), false, buffer);
		}
	}

	public void setFloat(String name, float value) {
		glUniform1f(glGetUniformLocation(program, name), value);
	}

	public void setVec3(String name, float x, float y, float z) {
		glUniform3f(glGetUniformLocation(program, name), x, y, z);
	}

	public void setInt(String name, int value) {
		glUniform1i(glGetUniformLocation(program, name), value);
	}

	public void destroy() {
		glDeleteProgram(program);
	}
}
