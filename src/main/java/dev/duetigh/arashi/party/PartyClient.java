package dev.duetigh.arashi.party;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import dev.duetigh.arashi.ArashiClient;

/**
 * WebSocket connection to the party relay server. Reconnects with exponential backoff on drop -
 * the server tolerates a reconnect mid-session (see PartyRegistry.onConnect on the server side,
 * which replaces any existing connection for the same UUID).
 */
final class PartyClient {
	private static final Gson GSON = new Gson();
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	private static final long MIN_BACKOFF_MS = 2_000L;
	private static final long MAX_BACKOFF_MS = 60_000L;

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
	private final BiConsumer<String, JsonObject> onMessage;
	private final Runnable onConnected;
	private final Runnable onDisconnected;

	private volatile WebSocket socket;
	private volatile boolean closing;
	private volatile long backoffMs = MIN_BACKOFF_MS;

	PartyClient(BiConsumer<String, JsonObject> onMessage, Runnable onConnected, Runnable onDisconnected) {
		this.onMessage = onMessage;
		this.onConnected = onConnected;
		this.onDisconnected = onDisconnected;
	}

	void connect(String serverUrl, Protocol.Hello hello) {
		closing = false;
		attemptConnect(serverUrl, hello);
	}

	void close() {
		closing = true;
		WebSocket current = socket;
		if (current != null) {
			current.sendClose(WebSocket.NORMAL_CLOSURE, "client closing").exceptionally(e -> null);
		}
	}

	boolean isConnected() {
		return socket != null;
	}

	void send(Object payload) {
		WebSocket current = socket;
		if (current == null) {
			return;
		}

		try {
			current.sendText(GSON.toJson(payload), true);
		} catch (RuntimeException e) {
			ArashiClient.LOGGER.warn("Arashi party: failed to send message", e);
		}
	}

	private void attemptConnect(String serverUrl, Protocol.Hello hello) {
		httpClient.newWebSocketBuilder()
				.connectTimeout(CONNECT_TIMEOUT)
				.buildAsync(URI.create(serverUrl), new Listener(serverUrl, hello))
				.exceptionally(e -> {
					ArashiClient.LOGGER.warn("Arashi party: failed to connect to relay at {}", serverUrl, e);
					onDisconnected.run();
					scheduleReconnect(serverUrl, hello);
					return null;
				});
	}

	private void scheduleReconnect(String serverUrl, Protocol.Hello hello) {
		if (closing) {
			return;
		}

		long delay = backoffMs;
		backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);

		Thread.ofVirtual().name("arashi-party-reconnect").start(() -> {
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			if (!closing) {
				attemptConnect(serverUrl, hello);
			}
		});
	}

	private final class Listener implements WebSocket.Listener {
		private final String serverUrl;
		private final Protocol.Hello hello;
		private final StringBuilder buffer = new StringBuilder();

		Listener(String serverUrl, Protocol.Hello hello) {
			this.serverUrl = serverUrl;
			this.hello = hello;
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			socket = webSocket;
			backoffMs = MIN_BACKOFF_MS;
			webSocket.sendText(GSON.toJson(hello), true);
			onConnected.run();
			WebSocket.Listener.super.onOpen(webSocket);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			buffer.append(data);
			webSocket.request(1);

			if (!last) {
				return CompletableFuture.completedFuture(null);
			}

			String message = buffer.toString();
			buffer.setLength(0);
			dispatch(message);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			socket = null;
			onDisconnected.run();

			if (!closing) {
				scheduleReconnect(serverUrl, hello);
			}

			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			socket = null;
			onDisconnected.run();
			scheduleReconnect(serverUrl, hello);
		}

		private void dispatch(String message) {
			try {
				JsonElement parsed = GSON.fromJson(message, JsonElement.class);
				if (parsed == null || !parsed.isJsonObject()) {
					return;
				}

				JsonObject obj = parsed.getAsJsonObject();
				JsonElement typeElement = obj.get("type");
				if (typeElement != null) {
					onMessage.accept(typeElement.getAsString(), obj);
				}
			} catch (JsonParseException e) {
				ArashiClient.LOGGER.warn("Arashi party: received malformed message", e);
			}
		}
	}
}
