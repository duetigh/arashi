package dev.duetigh.arashi.hypixel;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which Hypixel lobby instances (keyed by the "Server:" widget value, e.g. "mini40B" - not
 * IP, since IP stays constant across every lobby switch on Hypixel) have been seen this session.
 * Session-only: never persisted to disk, so it's naturally cleared on the next Minecraft launch.
 */
public final class LobbyTracker {
	private static final long DISPLAY_DURATION_MS = 3000L;
	private static final long FADE_DURATION_MS = 500L;

	private final Set<String> visitedLobbies = new HashSet<>();
	private String lastSeenLobby;
	private long lobbySearchedAtMillis = -1L;

	public void onLobbyObserved(String lobbyId) {
		if (lobbyId.equals(lastSeenLobby)) {
			return;
		}

		lastSeenLobby = lobbyId;

		if (!visitedLobbies.add(lobbyId)) {
			lobbySearchedAtMillis = System.currentTimeMillis();
		}
	}

	public boolean isShowingLobbySearched() {
		return lobbySearchedAtMillis >= 0 && System.currentTimeMillis() - lobbySearchedAtMillis < DISPLAY_DURATION_MS;
	}

	/** 1.0 while fully visible, linearly fading to 0.0 over the last {@link #FADE_DURATION_MS}. */
	public float fadeAlpha() {
		long elapsed = System.currentTimeMillis() - lobbySearchedAtMillis;
		long fadeStart = DISPLAY_DURATION_MS - FADE_DURATION_MS;

		if (elapsed <= fadeStart) {
			return 1.0f;
		}

		float fadeProgress = (elapsed - fadeStart) / (float) FADE_DURATION_MS;
		return Math.max(0.0f, 1.0f - fadeProgress);
	}
}
