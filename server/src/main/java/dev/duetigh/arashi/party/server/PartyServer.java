package dev.duetigh.arashi.party.server;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.function.Consumer;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/** Transport and message dispatch only - all party/invite logic lives in {@link PartyRegistry}. */
final class PartyServer extends WebSocketServer {
	private static final Gson GSON = new Gson();

	private final PartyRegistry registry = new PartyRegistry();

	PartyServer(int port) {
		super(new InetSocketAddress(port));
		setReuseAddr(true);
		setConnectionLostTimeout(30);
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		// Nothing to do until the client sends "hello" - see handleHello.
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		UUID uuid = uuidOf(conn);
		if (uuid != null) {
			registry.onDisconnect(uuid, conn);
		}
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		JsonObject obj;
		try {
			JsonElement parsed = GSON.fromJson(message, JsonElement.class);
			if (parsed == null || !parsed.isJsonObject()) {
				return;
			}
			obj = parsed.getAsJsonObject();
		} catch (JsonParseException e) {
			return;
		}

		JsonElement typeElement = obj.get("type");
		String type = typeElement == null ? null : typeElement.getAsString();
		if (type == null) {
			return;
		}

		try {
			switch (type) {
				case "hello" -> handleHello(conn, obj);
				case "invite" -> withUuid(conn, uuid -> registry.invite(uuid, GSON.fromJson(obj, Protocol.Invite.class).targetUsername));
				case "invite_response" -> withUuid(conn, uuid -> {
					Protocol.InviteResponse response = GSON.fromJson(obj, Protocol.InviteResponse.class);
					registry.respondToInvite(uuid, response.inviteId, response.accept);
				});
				case "kick" -> withUuid(conn, uuid -> {
					Protocol.Kick kick = GSON.fromJson(obj, Protocol.Kick.class);
					registry.kick(uuid, UUID.fromString(kick.targetUuid));
				});
				case "leave" -> withUuid(conn, registry::leave);
				case "lobby_seen" -> withUuid(conn, uuid -> registry.lobbySeen(uuid, GSON.fromJson(obj, Protocol.LobbySeenIn.class).lobbyId));
				default -> {
					// Unknown message type - ignore, keeps the door open for future non-breaking additions.
				}
			}
		} catch (JsonParseException | IllegalArgumentException | NullPointerException e) {
			conn.send(GSON.toJson(new Protocol.Error(Protocol.ERR_BAD_REQUEST, "Malformed " + type + " message")));
		}
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		System.err.println("Arashi party server connection error: " + ex);
	}

	@Override
	public void onStart() {
		System.out.println("Arashi party relay listening on port " + getPort());
	}

	void shutdown() throws InterruptedException {
		registry.shutdown();
		stop(1000);
	}

	private void handleHello(WebSocket conn, JsonObject obj) {
		Protocol.Hello hello = GSON.fromJson(obj, Protocol.Hello.class);

		if (hello == null || hello.protocolVersion != Protocol.PROTOCOL_VERSION) {
			conn.send(GSON.toJson(new Protocol.Error(Protocol.ERR_PROTOCOL_VERSION,
					"Server expects protocol version " + Protocol.PROTOCOL_VERSION)));
			conn.close(1000, "protocol version mismatch");
			return;
		}

		UUID uuid;
		try {
			uuid = UUID.fromString(hello.uuid);
		} catch (IllegalArgumentException | NullPointerException e) {
			conn.close(1000, "invalid uuid");
			return;
		}

		if (hello.username == null || hello.username.isBlank()) {
			conn.close(1000, "invalid username");
			return;
		}

		conn.setAttachment(uuid);
		registry.onConnect(uuid, hello.username, conn);
	}

	private static void withUuid(WebSocket conn, Consumer<UUID> action) {
		UUID uuid = uuidOf(conn);
		if (uuid != null) {
			action.accept(uuid);
		}
	}

	private static UUID uuidOf(WebSocket conn) {
		Object attachment = conn.getAttachment();
		return attachment instanceof UUID uuid ? uuid : null;
	}
}
