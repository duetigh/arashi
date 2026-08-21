package dev.duetigh.arashirender.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Spectator/freecam-style camera: while the cursor is locked (see {@link #update}'s {@code locked}
 * flag, toggled by the caller), mouse movement drives yaw/pitch and WASD + Space/Shift fly relative to
 * the view. Scroll adjusts field of view between a close-in zoom and the default, unzoomed FOV - there
 * is no zooming out past normal. Input is polled once per frame from {@link #update}, which the caller
 * skips while ImGui wants mouse/keyboard focus.
 */
public final class Camera {
	private static final float DEFAULT_FOV_DEGREES = 70f;
	private static final float MIN_FOV_DEGREES = 15f;
	private static final float DEFAULT_FRAME_DISTANCE = 40f;

	private final Vector3f position;
	private float yaw = -135f;
	private float pitch = -30f;
	private float fovDegrees = DEFAULT_FOV_DEGREES;
	private final float flySpeed = 10f;

	private double lastMouseX;
	private double lastMouseY;
	private boolean hasLastMouse;

	public Camera(Vector3f initialPosition) {
		this.position = new Vector3f(initialPosition);
	}

	public void update(long window, float deltaSeconds, double mouseX, double mouseY, double scrollDelta, boolean locked) {
		double dx = (locked && hasLastMouse) ? mouseX - lastMouseX : 0;
		double dy = (locked && hasLastMouse) ? mouseY - lastMouseY : 0;
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		hasLastMouse = true;

		if (!locked) {
			return;
		}

		yaw += (float) dx * 0.15f;
		pitch = clamp(pitch - (float) dy * 0.15f, -89f, 89f);

		Vector3f forward = forward();
		Vector3f right = new Vector3f(forward).cross(new Vector3f(0, 1, 0)).normalize();
		Vector3f up = new Vector3f(0, 1, 0);
		float move = flySpeed * deltaSeconds;

		if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
			position.fma(move, forward);
		}
		if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
			position.fma(-move, forward);
		}
		if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
			position.fma(-move, right);
		}
		if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
			position.fma(move, right);
		}
		if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
			position.fma(move, up);
		}
		if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
			position.fma(-move, up);
		}

		if (scrollDelta != 0) {
			// Scrolling "up" (positive delta) zooms in; capped at the default FOV so you can never zoom out
			// past the normal view.
			fovDegrees = clamp(fovDegrees - (float) scrollDelta * 2f, MIN_FOV_DEGREES, DEFAULT_FOV_DEGREES);
		}
	}

	/** Repositions the camera to frame a point of interest (e.g. centering on a newly loaded scan). */
	public void frame(Vector3f target) {
		position.set(new Vector3f(target).fma(-DEFAULT_FRAME_DISTANCE, forward()));
	}

	/** Called whenever cursor lock is toggled, so the next mouse delta doesn't include the jump from it. */
	public void resetMouseTracking() {
		hasLastMouse = false;
	}

	public Vector3f eye() {
		return new Vector3f(position);
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
		return new Matrix4f().perspective((float) Math.toRadians(fovDegrees), aspect, 0.05f, 10000f);
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
