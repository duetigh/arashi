package dev.duetigh.arashirender.input;

import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Edge-detects "just pressed" key transitions across frames, for actions that should fire once per
 * press (delete, undo, redo) rather than every frame a key is held (unlike the continuous
 * {@code glfwGetKey} polling used for movement).
 */
public final class KeyEdge {
	private final Set<Integer> held = new HashSet<>();

	/** True only on the frame a key transitions from not-held to held. */
	public boolean pressed(long window, int key) {
		boolean down = glfwGetKey(window, key) == GLFW_PRESS;

		if (down) {
			return held.add(key);
		}

		held.remove(key);
		return false;
	}

	public static boolean ctrlDown(long window) {
		return glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS
				|| glfwGetKey(window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS
				|| glfwGetKey(window, GLFW_KEY_LEFT_SUPER) == GLFW_PRESS
				|| glfwGetKey(window, GLFW_KEY_RIGHT_SUPER) == GLFW_PRESS;
	}

	public static boolean shiftDown(long window) {
		return glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS
				|| glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
	}
}
