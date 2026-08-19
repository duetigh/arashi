package dev.duetigh.arashi.party;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import dev.duetigh.arashi.ArashiClient;
import dev.duetigh.arashi.hypixel.LobbyTracker;

/**
 * Party roster/invite state and the invite/respond/kick/leave API. Connects at mod init (not
 * gated on being in a world) since invites work globally by username. All state mutation and
 * listener callbacks are marshalled onto the client thread, since updates originate on the
 * WebSocket's own background thread but touch state the GUI reads during rendering.
 */
public final class PartyManager {
	public enum ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

	public record IncomingInvite(String inviteId, UUID fromUuid, String fromUsername, long expiresAt) {
	}

	public record OutgoingInvite(String inviteId, UUID toUuid, String toUsername, long expiresAt) {
	}

	public record InviteOutcome(UUID targetUuid, String targetUsername, boolean accepted, String reason) {
	}

	private static final Gson GSON = new Gson();

	private final LobbyTracker lobbyTracker;
	private final PartyClient client;

	private UUID selfUuid;
	private UUID leaderUuid;
	private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
	private List<PartyMember> members = List.of();
	private final Map<String, IncomingInvite> incomingInvites = new LinkedHashMap<>();
	private final Map<String, OutgoingInvite> outgoingInvites = new LinkedHashMap<>();

	private Consumer<IncomingInvite> onInviteReceived = invite -> { };
	private Consumer<InviteOutcome> onInviteResult = outcome -> { };
	private Runnable onKicked = () -> { };
	private Runnable onRosterChanged = () -> { };
	private Runnable onConnectionStateChanged = () -> { };

	public PartyManager(LobbyTracker lobbyTracker) {
		this.lobbyTracker = lobbyTracker;
		this.client = new PartyClient(this::onMessage, this::onConnected, this::onDisconnected);
		lobbyTracker.setNewLobbyListener(this::onLocalLobbyDiscovered);
	}

	public void connect(String serverUrl) {
		User user = Minecraft.getInstance().getUser();
		if (user == null) {
			return;
		}

		selfUuid = user.getProfileId();
		String modVersion = FabricLoader.getInstance()
				.getModContainer(ArashiClient.MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("?");

		connectionState = ConnectionState.CONNECTING;
		onConnectionStateChanged.run();
		client.connect(serverUrl, new Protocol.Hello(selfUuid.toString(), user.getName(), modVersion));
	}

	public void invite(String username) {
		if (username != null && !username.isBlank()) {
			client.send(new Protocol.Invite(username));
		}
	}

	public void respondToInvite(String inviteId, boolean accept) {
		incomingInvites.remove(inviteId);
		client.send(new Protocol.InviteResponse(inviteId, accept));
		onRosterChanged.run();
	}

	public void kick(UUID targetUuid) {
		client.send(new Protocol.Kick(targetUuid.toString()));
	}

	public void leave() {
		client.send(new Protocol.Leave());
	}

	public ConnectionState connectionState() {
		return connectionState;
	}

	public UUID selfUuid() {
		return selfUuid;
	}

	public UUID leaderUuid() {
		return leaderUuid;
	}

	public boolean isSelfLeader() {
		return selfUuid != null && selfUuid.equals(leaderUuid);
	}

	public List<PartyMember> members() {
		return members;
	}

	public List<IncomingInvite> incomingInvites() {
		long now = System.currentTimeMillis();
		return incomingInvites.values().stream().filter(invite -> invite.expiresAt() > now).toList();
	}

	public List<OutgoingInvite> outgoingInvites() {
		long now = System.currentTimeMillis();
		return outgoingInvites.values().stream().filter(invite -> invite.expiresAt() > now).toList();
	}

	public void setOnInviteReceived(Consumer<IncomingInvite> listener) {
		this.onInviteReceived = listener != null ? listener : invite -> { };
	}

	public void setOnInviteResult(Consumer<InviteOutcome> listener) {
		this.onInviteResult = listener != null ? listener : outcome -> { };
	}

	public void setOnKicked(Runnable listener) {
		this.onKicked = listener != null ? listener : () -> { };
	}

	public void setOnRosterChanged(Runnable listener) {
		this.onRosterChanged = listener != null ? listener : () -> { };
	}

	public void setOnConnectionStateChanged(Runnable listener) {
		this.onConnectionStateChanged = listener != null ? listener : () -> { };
	}

	private void onLocalLobbyDiscovered(String lobbyId) {
		client.send(new Protocol.LobbySeenIn(lobbyId));
	}

	private void onConnected() {
		// The relay handshake isn't complete until "hello_ack" arrives - see handleMessage.
	}

	private void onDisconnected() {
		Minecraft.getInstance().execute(() -> {
			connectionState = ConnectionState.CONNECTING;
			onConnectionStateChanged.run();
		});
	}

	private void onMessage(String type, JsonObject obj) {
		Minecraft.getInstance().execute(() -> handleMessage(type, obj));
	}

	private void handleMessage(String type, JsonObject obj) {
		switch (type) {
			case "hello_ack" -> {
				connectionState = ConnectionState.CONNECTED;
				onConnectionStateChanged.run();
			}
			case "error" -> {
				Protocol.Error error = GSON.fromJson(obj, Protocol.Error.class);
				ArashiClient.LOGGER.warn("Arashi party: server error {} - {}", error.code, error.message);
			}
			case "invite_received" -> {
				Protocol.InviteReceived received = GSON.fromJson(obj, Protocol.InviteReceived.class);
				IncomingInvite invite = new IncomingInvite(received.inviteId, UUID.fromString(received.fromUuid),
						received.fromUsername, received.expiresAt);
				incomingInvites.put(invite.inviteId(), invite);
				onInviteReceived.accept(invite);
				onRosterChanged.run();
			}
			case "invite_sent_ack" -> {
				Protocol.InviteSentAck ack = GSON.fromJson(obj, Protocol.InviteSentAck.class);
				outgoingInvites.put(ack.inviteId, new OutgoingInvite(ack.inviteId, UUID.fromString(ack.toUuid), ack.toUsername, ack.expiresAt));
				onRosterChanged.run();
			}
			case "invite_result" -> {
				Protocol.InviteResult result = GSON.fromJson(obj, Protocol.InviteResult.class);
				outgoingInvites.remove(result.inviteId);
				onInviteResult.accept(new InviteOutcome(UUID.fromString(result.targetUuid), result.targetUsername,
						result.accepted, result.reason));
				onRosterChanged.run();
			}
			case "party_update" -> {
				Protocol.PartyUpdate update = GSON.fromJson(obj, Protocol.PartyUpdate.class);
				leaderUuid = UUID.fromString(update.leaderUuid);
				members = update.members.stream()
						.map(m -> new PartyMember(UUID.fromString(m.uuid), m.username, m.online))
						.toList();
				onRosterChanged.run();
			}
			case "lobby_seen" -> {
				Protocol.LobbySeenOut lobbySeen = GSON.fromJson(obj, Protocol.LobbySeenOut.class);
				lobbyTracker.addKnownLobby(lobbySeen.lobbyId);
			}
			case "kicked" -> {
				incomingInvites.clear();
				outgoingInvites.clear();
				onKicked.run();
			}
			default -> {
				// Unknown message type - ignore, keeps the door open for future non-breaking additions.
			}
		}
	}
}
