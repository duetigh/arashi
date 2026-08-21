package dev.duetigh.arashirender;

import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;

import dev.duetigh.arashirender.input.KeyEdge;
import dev.duetigh.arashirender.render.Camera;
import dev.duetigh.arashirender.render.OutlineRenderer;
import dev.duetigh.arashirender.render.RenderMode;
import dev.duetigh.arashirender.render.SceneRenderer;
import dev.duetigh.arashirender.ui.LibraryScreen;
import dev.duetigh.arashirender.ui.UiPanel;
import dev.duetigh.arashirender.update.UpdateChecker;
import dev.duetigh.arashirender.world.Raycaster;
import dev.duetigh.arashirender.world.VoxelWorld;

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
		UpdateChecker.checkAsync(info -> state.availableUpdate = info);
		SceneRenderer renderer = new SceneRenderer();
		OutlineRenderer outline = new OutlineRenderer();
		Camera camera = new Camera(new Vector3f(0, 80, 0));
		UiPanel panel = new UiPanel();
		LibraryScreen libraryScreen = new LibraryScreen();
		KeyEdge keyEdge = new KeyEdge();
		boolean leftMouseWasDown = false;
		boolean cameraLocked = false;

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
			boolean capturingKeyboard = ImGui.getIO().getWantCaptureKeyboard();
			double[] mouseX = new double[1];
			double[] mouseY = new double[1];
			glfwGetCursorPos(window, mouseX, mouseY);

			boolean inViewer = state.screen == AppState.Screen.VIEWER && state.hasScan();
			Raycaster.Hit hit = null;

			if (!inViewer && cameraLocked) {
				cameraLocked = false;
				glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
				camera.resetMouseTracking();
			}

			if (inViewer) {
				camera.update(window, delta, mouseX[0], mouseY[0], capturingMouse ? 0 : ImGui.getIO().getMouseWheel(), cameraLocked);

				if (state.pendingCameraTarget != null) {
					camera.frame(state.pendingCameraTarget);
					state.pendingCameraTarget = null;
				}

				if (!capturingMouse) {
					hit = Raycaster.cast(state.world, state.filters, camera.eye(), camera.forward(), RAYCAST_MAX_DISTANCE);
				}

				boolean leftMouseDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;

				if (leftMouseDown && !leftMouseWasDown && !capturingMouse) {
					state.selectedKey = hit != null ? VoxelWorld.pack(hit.x(), hit.y(), hit.z()) : null;
				}

				leftMouseWasDown = leftMouseDown;

				if (!capturingKeyboard) {
					if (keyEdge.pressed(window, GLFW_KEY_L)) {
						cameraLocked = !cameraLocked;
						glfwSetInputMode(window, GLFW_CURSOR, cameraLocked ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
						camera.resetMouseTracking();
					} else if (cameraLocked && keyEdge.pressed(window, GLFW_KEY_ESCAPE)) {
						cameraLocked = false;
						glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
						camera.resetMouseTracking();
					}

					if (keyEdge.pressed(window, GLFW_KEY_DELETE) || keyEdge.pressed(window, GLFW_KEY_BACKSPACE)) {
						state.deleteSelected();
					}

					boolean ctrlZ = KeyEdge.ctrlDown(window) && keyEdge.pressed(window, GLFW_KEY_Z);
					boolean ctrlY = KeyEdge.ctrlDown(window) && keyEdge.pressed(window, GLFW_KEY_Y);

					if (ctrlZ) {
						if (KeyEdge.shiftDown(window)) {
							state.redo();
						} else {
							state.undo();
						}
					} else if (ctrlY) {
						state.redo();
					}
				}
			} else {
				leftMouseWasDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
			}

			int[] width = new int[1];
			int[] height = new int[1];
			glfwGetFramebufferSize(window, width, height);
			glViewport(0, 0, width[0], height[0]);
			glClearColor(0.08f, 0.09f, 0.11f, 1f);
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

			if (inViewer) {
				if (state.needsRebuild) {
					Map<Integer, Integer> textureIds = state.settings.renderMode == RenderMode.TEXTURE
							? buildTextureIdMap(state)
							: Map.of();
					renderer.rebuild(state.world, state.filters, state.palette, state.settings.renderMode, textureIds);
					state.needsRebuild = false;
				}

				float aspect = (float) width[0] / Math.max(1, height[0]);
				var view = camera.viewMatrix();
				var proj = camera.projectionMatrix(aspect);
				renderer.render(view, proj, state.filters.globalOpacity());

				if (hit != null) {
					outline.draw(view, proj, hit.x(), hit.y(), hit.z(), 1f, 1f, 1f, 0.35f);
				}

				if (state.selectedKey != null) {
					long key = state.selectedKey;
					outline.draw(view, proj, VoxelWorld.unpackX(key), VoxelWorld.unpackY(key), VoxelWorld.unpackZ(key),
							1f, 0.82f, 0.2f, 0.9f);
				}
			}

			if (state.screen == AppState.Screen.LIBRARY) {
				libraryScreen.draw(state);
			} else {
				panel.draw(state, hit);
			}

			ImGui.render();
			imGuiGl3.renderDrawData(ImGui.getDrawData());

			glfwSwapBuffers(window);
		}

		state.textureLoader.close();
		outline.destroy();
		renderer.destroy();
		imGuiGl3.shutdown();
		imGuiGlfw.shutdown();
		ImGui.destroyContext();
		glfwDestroyWindow(window);
		glfwTerminate();
	}

	private static Map<Integer, Integer> buildTextureIdMap(AppState state) {
		Map<Integer, Integer> ids = new HashMap<>();
		String[] palette = state.world.palette();

		for (int i = 0; i < palette.length; i++) {
			Integer textureId = state.textureLoader.textureIdFor(palette[i]);

			if (textureId != null) {
				ids.put(i, textureId);
			}
		}

		return ids;
	}
}
