package dev.duetigh.arashirender;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;

import dev.duetigh.arashirender.render.Camera;
import dev.duetigh.arashirender.render.SceneRenderer;
import dev.duetigh.arashirender.ui.UiPanel;
import dev.duetigh.arashirender.world.Raycaster;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

public final class Main {
	private static final float RAYCAST_MAX_DISTANCE = 300f;

	public static void main(String[] args) {
		GLFWErrorCallback.createPrint(System.err).set();

		if (!glfwInit()) {
			throw new IllegalStateException("Failed to initialize GLFW");
		}

		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

		long window = glfwCreateWindow(1280, 800, "Arashi Render", 0, 0);

		if (window == 0) {
			throw new IllegalStateException("Failed to create GLFW window");
		}

		glfwMakeContextCurrent(window);
		glfwSwapInterval(1);
		glfwShowWindow(window);
		GL.createCapabilities();

		ImGui.createContext();
		ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
		imGuiGlfw.init(window, true);
		ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
		imGuiGl3.init("#version 330");

		glEnable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		AppState state = new AppState();
		SceneRenderer renderer = new SceneRenderer();
		Camera camera = new Camera(new Vector3f(0, 80, 0));
		UiPanel panel = new UiPanel();

		double lastTime = glfwGetTime();

		while (!glfwWindowShouldClose(window)) {
			double now = glfwGetTime();
			float delta = (float) (now - lastTime);
			lastTime = now;

			glfwPollEvents();
			imGuiGl3.newFrame();
			imGuiGlfw.newFrame();
			ImGui.newFrame();

			boolean capturingMouse = ImGui.getIO().getWantCaptureMouse();
			double[] mouseX = new double[1];
			double[] mouseY = new double[1];
			glfwGetCursorPos(window, mouseX, mouseY);

			if (!capturingMouse) {
				camera.update(window, delta, mouseX[0], mouseY[0], ImGui.getIO().getMouseWheel());
			} else {
				camera.update(window, delta, mouseX[0], mouseY[0], 0);
			}

			Raycaster.Hit hit = null;

			if (state.hasScan() && !capturingMouse) {
				hit = Raycaster.cast(state.world, state.filters, camera.eye(), camera.forward(), RAYCAST_MAX_DISTANCE);
			}

			int[] width = new int[1];
			int[] height = new int[1];
			glfwGetFramebufferSize(window, width, height);
			glViewport(0, 0, width[0], height[0]);
			glClearColor(0.08f, 0.09f, 0.11f, 1f);
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

			if (state.needsRebuild && state.hasScan()) {
				renderer.rebuild(state.world, state.filters, state.palette);
				state.needsRebuild = false;
			}

			if (state.hasScan()) {
				float aspect = (float) width[0] / Math.max(1, height[0]);
				renderer.render(camera.viewMatrix(), camera.projectionMatrix(aspect), state.filters.globalOpacity());
			}

			panel.draw(state, hit);

			ImGui.render();
			imGuiGl3.renderDrawData(ImGui.getDrawData());

			glfwSwapBuffers(window);
		}

		renderer.destroy();
		imGuiGl3.shutdown();
		imGuiGlfw.shutdown();
		ImGui.destroyContext();
		glfwDestroyWindow(window);
		glfwTerminate();
	}
}
