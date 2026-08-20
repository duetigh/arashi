package dev.duetigh.arashirender.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Blender-style camera: middle-mouse-drag orbits a pivot, right-mouse-drag switches to WASD fly +
 * mouse-look, scroll zooms (orbit) or adjusts fly speed (fly). Input is polled once per frame from
 * {@link #update}, which the caller skips while ImGui wants mouse/keyboard focus.
 */
public final class Camera {
	private final Vector3f target;
	private float yaw = -135f;
	private float pitch = -30f;
	private float distance = 40f;
	private float flySpeed = 10f;

	private double lastMouseX;
	private double lastMouseY;
	private boolean dragging;

	public Camera(Vector3f initialTarget) {
		this.target = new Vector3f(initialTarget);
	}

	public void update(long window, float deltaSeconds, double mouseX, double mouseY, double scrollDelta) {
		boolean middleDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_MIDDLE) == GLFW_PRESS;
		boolean rightDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;

		double dx = dragging ? mouseX - lastMouseX : 0;
		double dy = dragging ? mouseY - lastMouseY : 0;
		lastMouseX = mouseX;
		lastMouseY = mouseY;

		if (middleDown || rightDown) {
			dragging = true;
			yaw += (float) dx * 0.25f;
			pitch = clamp(pitch - (float) dy * 0.25f, -89f, 89f);
		} else {
			dragging = false;
		}

		Vector3f forward = forward();

		if (rightDown) {
			Vector3f right = new Vector3f(forward).cross(new Vector3f(0, 1, 0)).normalize();
			Vector3f up = new Vector3f(0, 1, 0);
			float move = flySpeed * deltaSeconds;

			if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
				target.fma(move, forward);
			}
			if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
				target.fma(-move, forward);
			}
			if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
				target.fma(-move, right);
			}
			if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
				target.fma(move, right);
			}
			if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
				target.fma(move, up);
			}
			if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
				target.fma(-move, up);
			}

			if (scrollDelta != 0) {
				flySpeed = Math.max(0.5f, flySpeed * (float) Math.pow(1.15, scrollDelta));
			}
		} else if (scrollDelta != 0) {
			distance = Math.max(1.0f, distance * (float) Math.pow(0.9, scrollDelta));
		}
	}

	public Vector3f eye() {
		return new Vector3f(target).fma(-distance, forward());
	}

	public Vector3f forward() {
		float yawRad = (float) Math.toRadians(yaw);
		float pitchRad = (float) Math.toRadians(pitch);
		return new Vector3f(
				(float) (Math.cos(pitchRad) * Math.cos(yawRad)),
				(float) Math.sin(pitchRad),
				(float) (Math.cos(pitchRad) * Math.sin(yawRad))).normalize();
	}

	public Matrix4f viewMatrix() {
		Vector3f eye = eye();
		return new Matrix4f().lookAt(eye, new Vector3f(eye).add(forward()), new Vector3f(0, 1, 0));
	}

	public Matrix4f projectionMatrix(float aspect) {
		return new Matrix4f().perspective((float) Math.toRadians(70), aspect, 0.05f, 10000f);
	}

	public float flySpeed() {
		return flySpeed;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
