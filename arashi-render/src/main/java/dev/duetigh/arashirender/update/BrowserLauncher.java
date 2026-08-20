package dev.duetigh.arashirender.update;

import java.io.IOException;

/**
 * Opens a URL in the system browser via a plain OS process, not {@code java.awt.Desktop}. Desktop's
 * browse() needs AppKit on the main thread on macOS, which conflicts with GLFW already owning the
 * main thread there via {@code -XstartOnFirstThread} (see Main/build.gradle) - launching a separate
 * process sidesteps that entirely.
 */
public final class BrowserLauncher {
	private BrowserLauncher() {
	}

	public static void open(String url) {
		String os = System.getProperty("os.name", "").toLowerCase();

		try {
			if (os.contains("mac")) {
				new ProcessBuilder("open", url).start();
			} else if (os.contains("win")) {
				new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
			} else {
				new ProcessBuilder("xdg-open", url).start();
			}
		} catch (IOException ignored) {
			// Best-effort - worst case the user opens the release page manually.
		}
	}
}
