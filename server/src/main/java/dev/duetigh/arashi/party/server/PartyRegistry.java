package dev.duetigh.arashi.party.server;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;

import com.google.gson.Gson;

/**
 * All party/invite state and business logic, in memory only (a server restart clears everything).
 * A player with no entry in {@link #partyByMember} is implicitly in a solo party of themselves.
 * Every public method is synchronized on this instance - traffic is small enough (a handful of
 * players, occasional messages) that a single lock is simpler than finer-grained concurrency.
 */
final class PartyRegistry {
	private static final int MAX_PARTY_SIZE = 10;
	private static final long INVITE_TTL_MS = 60_000L;
	private static final long RATE_LIMIT_WINDOW_MS = 60_000L;
	private static final int RATE_LIMIT_MAX_INVITES = 5;

	private static final Gson GSON = new Gson();

	private final Map<UUID, ConnectedPlayer> online = new HashMap<>();
	private final Map<UUID, String> knownUsernames = new HashMap<>();
	private final Map<UUID, Party> partyByMember = new HashMap<>();
	private final Map<String, PendingInvite> invitesById = new HashMap<>();
	private final Map<UUID, Deque<Long>> inviteTimestamps = new HashMap<>();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "arashi-party-invite-sweep");
		thread.setDaemon(true);
		return thread;
	});

	PartyRegistry() {
		scheduler.scheduleAtFixedRate(this::sweepExpiredInvites, 30, 30, TimeUnit.SECONDS);
	}

	synchronized void onConnect(UUID uuid, String username, WebSocket socket) {
		knownUsernames.put(uuid, username);

		ConnectedPlayer existing = online.get(uuid);
		if (existing != null) {
			try {
				existing.socket.close(4000, "Replaced by a new connection for the same account");
			} catch (Exception ignored) {
				// Already closing/closed - fine, we're about to overwrite the registry entry anyway.
			}
		}

		online.put(uuid, new ConnectedPlayer(uuid, username, socket));
		send(socket, new Protocol.HelloAck(uuid.toString()));

		// Always resync this player's own roster view on (re)connect, even if they're solo now -
		// e.g. they were kicked or their party leader dissolved the party while they were offline,
		// and their local client otherwise has no way to learn its cached roster went stale.
		Party party = partyByMember.get(uuid);
		if (party != null) {
			broadcastPartyUpdate(party);
		} else {
			send(socket, soloPartyUpdate(uuid));
		}
	}

	/** {@code conn} must be the connection that's disconnecting - guards against a stale close from a just-replaced socket. */
	synchronized void onDisconnect(UUID uuid, WebSocket conn) {
		ConnectedPlayer current = online.get(uuid);
		if (current == null || current.socket != conn) {
			return;
		}

		online.remove(uuid);

		Party party = partyByMember.get(uuid);
		if (party != null) {
			broadcastPartyUpdate(party);
		}
	}

	synchronized void invite(UUID fromUuid, String targetUsername) {
		ConnectedPlayer inviter = online.get(fromUuid);
		if (inviter == null || targetUsername == null) {
			return;
		}

		if (!checkAndRecordRateLimit(fromUuid)) {
			send(inviter.socket, new Protocol.Error(Protocol.ERR_RATE_LIMITED, "Too many invites sent recently"));
			return;
		}

		ConnectedPlayer target = findOnlineByUsername(targetUsername);
		if (target == null) {
			send(inviter.socket, new Protocol.Error(Protocol.ERR_USER_OFFLINE, targetUsername + " is not online"));
			return;
		}

		if (target.uuid.equals(fromUuid)) {
			send(inviter.socket, new Protocol.Error(Protocol.ERR_CANNOT_INVITE_SELF, "You can't invite yourself"));
			return;
		}

		Party inviterParty = partyByMember.get(fromUuid);
		if (inviterParty != null) {
			if (inviterParty.members.contains(target.uuid)) {
				send(inviter.socket, new Protocol.Error(Protocol.ERR_ALREADY_MEMBER, target.username + " is already in your party"));
				return;
			}
			if (inviterParty.members.size() >= MAX_PARTY_SIZE) {
				send(inviter.socket, new Protocol.Error(Protocol.ERR_PARTY_FULL, "Your party is full"));
				return;
			}
		}

		Party targetParty = partyByMember.get(target.uuid);
		if (targetParty != null && targetParty.members.size() > 1) {
			send(inviter.socket, new Protocol.Error(Protocol.ERR_ALREADY_IN_PARTY, target.username + " is already in a party"));
			return;
		}

		String inviteId = UUID.randomUUID().toString();
		long expiresAt = System.currentTimeMillis() + INVITE_TTL_MS;
		invitesById.put(inviteId, new PendingInvite(inviteId, fromUuid, target.uuid, expiresAt));

		send(target.socket, new Protocol.InviteReceived(inviteId, fromUuid.toString(), inviter.username, expiresAt));
		send(inviter.socket, new Protocol.InviteSentAck(inviteId, target.uuid.toString(), target.username, expiresAt));
	}

	synchronized void respondToInvite(UUID responderUuid, String inviteId, boolean accept) {
		PendingInvite invite = inviteId == null ? null : invitesById.remove(inviteId);
		if (invite == null || !invite.targetUuid().equals(responderUuid)) {
			ConnectedPlayer responder = online.get(responderUuid);
			if (responder != null) {
				send(responder.socket, new Protocol.Error(Protocol.ERR_UNKNOWN_INVITE, "That invite no longer exists"));
			}
			return;
		}

		ConnectedPlayer inviter = online.get(invite.fromUuid());

		if (System.currentTimeMillis() > invite.expiresAt()) {
			if (inviter != null) {
				send(inviter.socket, new Protocol.InviteResult(invite.inviteId(), responderUuid.toString(),
						usernameOf(responderUuid), false, "EXPIRED"));
			}
			return;
		}

		if (!accept) {
			if (inviter != null) {
				send(inviter.socket, new Protocol.InviteResult(invite.inviteId(), responderUuid.toString(),
						usernameOf(responderUuid), false, "DECLINED"));
			}
			return;
		}

		Party party = partyByMember.get(invite.fromUuid());
		if (party == null) {
			party = new Party(invite.fromUuid());
			partyByMember.put(invite.fromUuid(), party);
		}

		if (party.members.size() >= MAX_PARTY_SIZE) {
			if (inviter != null) {
				send(inviter.socket, new Protocol.Error(Protocol.ERR_PARTY_FULL, "Your party is full"));
			}
			ConnectedPlayer responder = online.get(responderUuid);
			if (responder != null) {
				send(responder.socket, new Protocol.Error(Protocol.ERR_PARTY_FULL, "That party is now full"));
			}
			return;
		}

		partyByMember.remove(responderUuid);
		party.members.add(responderUuid);
		partyByMember.put(responderUuid, party);

		broadcastPartyUpdate(party);

		if (inviter != null) {
			send(inviter.socket, new Protocol.InviteResult(invite.inviteId(), responderUuid.toString(),
					usernameOf(responderUuid), true, null));
		}
	}

	synchronized void kick(UUID leaderUuid, UUID targetUuid) {
		Party party = partyByMember.get(leaderUuid);
		if (party == null || !party.leaderUuid.equals(leaderUuid)) {
			ConnectedPlayer leader = online.get(leaderUuid);
			if (leader != null) {
				send(leader.socket, new Protocol.Error(Protocol.ERR_NOT_LEADER, "Only the party leader can remove members"));
			}
			return;
		}

		if (targetUuid.equals(leaderUuid) || !party.members.contains(targetUuid)) {
			return;
		}

		party.members.remove(targetUuid);
		partyByMember.remove(targetUuid);

		ConnectedPlayer target = online.get(targetUuid);
		if (target != null) {
			send(target.socket, new Protocol.Kicked(leaderUuid.toString()));
			send(target.socket, soloPartyUpdate(targetUuid));
		}

		if (party.members.size() <= 1) {
			partyByMember.remove(party.leaderUuid);
		} else {
			broadcastPartyUpdate(party);
		}
	}

	synchronized void leave(UUID uuid) {
		Party party = partyByMember.get(uuid);
		if (party == null) {
			return;
		}

		party.members.remove(uuid);
		partyByMember.remove(uuid);

		ConnectedPlayer self = online.get(uuid);
		if (self != null) {
			send(self.socket, soloPartyUpdate(uuid));
		}

		if (party.members.isEmpty()) {
			return;
		}

		if (party.leaderUuid.equals(uuid)) {
			party.leaderUuid = party.members.iterator().next();
		}

		if (party.members.size() == 1) {
			partyByMember.remove(party.members.iterator().next());
			return;
		}

		broadcastPartyUpdate(party);
	}

	synchronized void lobbySeen(UUID fromUuid, String lobbyId) {
		if (lobbyId == null || lobbyId.isBlank()) {
			return;
		}

		Party party = partyByMember.get(fromUuid);
		if (party == null) {
			return;
		}

		for (UUID member : party.members) {
			if (member.equals(fromUuid)) {
				continue;
			}
			ConnectedPlayer target = online.get(member);
			if (target != null) {
				send(target.socket, new Protocol.LobbySeenOut(fromUuid.toString(), lobbyId));
			}
		}
	}

	synchronized void shutdown() {
		scheduler.shutdownNow();
	}

	private void sweepExpiredInvites() {
		synchronized (this) {
			long now = System.currentTimeMillis();
			Iterator<Map.Entry<String, PendingInvite>> it = invitesById.entrySet().iterator();

			while (it.hasNext()) {
				PendingInvite invite = it.next().getValue();
				if (now <= invite.expiresAt()) {
					continue;
				}

				it.remove();
				ConnectedPlayer inviter = online.get(invite.fromUuid());
				if (inviter != null) {
					send(inviter.socket, new Protocol.InviteResult(invite.inviteId(), invite.targetUuid().toString(),
							usernameOf(invite.targetUuid()), false, "EXPIRED"));
				}
			}
		}
	}

	private void broadcastPartyUpdate(Party party) {
		List<Protocol.MemberInfo> members = party.members.stream()
				.map(m -> new Protocol.MemberInfo(m.toString(), usernameOf(m), online.containsKey(m)))
				.toList();
		Protocol.PartyUpdate update = new Protocol.PartyUpdate(party.leaderUuid.toString(), members);

		for (UUID member : party.members) {
			ConnectedPlayer connected = online.get(member);
			if (connected != null) {
				send(connected.socket, update);
			}
		}
	}

	private Protocol.PartyUpdate soloPartyUpdate(UUID uuid) {
		return new Protocol.PartyUpdate(uuid.toString(),
				List.of(new Protocol.MemberInfo(uuid.toString(), usernameOf(uuid), true)));
	}

	private ConnectedPlayer findOnlineByUsername(String username) {
		for (ConnectedPlayer player : online.values()) {
			if (player.username.equalsIgnoreCase(username)) {
				return player;
			}
		}
		return null;
	}

	private String usernameOf(UUID uuid) {
		return knownUsernames.getOrDefault(uuid, uuid.toString());
	}

	private boolean checkAndRecordRateLimit(UUID uuid) {
		Deque<Long> stamps = inviteTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
		long now = System.currentTimeMillis();

		while (!stamps.isEmpty() && now - stamps.peekFirst() > RATE_LIMIT_WINDOW_MS) {
			stamps.pollFirst();
		}

		if (stamps.size() >= RATE_LIMIT_MAX_INVITES) {
			return false;
		}

		stamps.addLast(now);
		return true;
	}

	private static void send(WebSocket socket, Object payload) {
		try {
			socket.send(GSON.toJson(payload));
		} catch (Exception ignored) {
			// The socket may have just closed from under us; onClose will clean up registry state.
		}
	}

	private record PendingInvite(String inviteId, UUID fromUuid, UUID targetUuid, long expiresAt) {
	}
}
