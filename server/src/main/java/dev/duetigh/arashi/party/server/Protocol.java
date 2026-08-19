package dev.duetigh.arashi.party.server;

import java.util.List;

/**
 * Gson DTOs for the party relay's JSON-over-WebSocket protocol, one object per text frame,
 * discriminated by a "type" field. Hand-mirrored on the mod client side (party/Protocol.java)
 * rather than shared, since this server and the mod are intentionally separate Gradle builds;
 * {@link #PROTOCOL_VERSION} guards against the two copies drifting silently.
 */
final class Protocol {
	static final int PROTOCOL_VERSION = 1;

	static final String ERR_USER_OFFLINE = "USER_OFFLINE";
	static final String ERR_ALREADY_IN_PARTY = "ALREADY_IN_PARTY";
	static final String ERR_ALREADY_MEMBER = "ALREADY_MEMBER";
	static final String ERR_CANNOT_INVITE_SELF = "CANNOT_INVITE_SELF";
	static final String ERR_NOT_LEADER = "NOT_LEADER";
	static final String ERR_RATE_LIMITED = "RATE_LIMITED";
	static final String ERR_UNKNOWN_INVITE = "UNKNOWN_INVITE";
	static final String ERR_PARTY_FULL = "PARTY_FULL";
	static final String ERR_BAD_REQUEST = "BAD_REQUEST";
	static final String ERR_PROTOCOL_VERSION = "PROTOCOL_VERSION";

	private Protocol() {
	}

	// ---- Client -> Server ----

	static final class Hello {
		int protocolVersion;
		String uuid;
		String username;
		String clientVersion;
	}

	static final class Invite {
		String targetUsername;
	}

	static final class InviteResponse {
		String inviteId;
		boolean accept;
	}

	static final class Kick {
		String targetUuid;
	}

	static final class LobbySeenIn {
		String lobbyId;
	}

	// ---- Server -> Client ----

	static final class HelloAck {
		final String type = "hello_ack";
		final String uuid;

		HelloAck(String uuid) {
			this.uuid = uuid;
		}
	}

	static final class Error {
		final String type = "error";
		final String code;
		final String message;

		Error(String code, String message) {
			this.code = code;
			this.message = message;
		}
	}

	static final class InviteReceived {
		final String type = "invite_received";
		final String inviteId;
		final String fromUuid;
		final String fromUsername;
		final long expiresAt;

		InviteReceived(String inviteId, String fromUuid, String fromUsername, long expiresAt) {
			this.inviteId = inviteId;
			this.fromUuid = fromUuid;
			this.fromUsername = fromUsername;
			this.expiresAt = expiresAt;
		}
	}

	static final class InviteSentAck {
		final String type = "invite_sent_ack";
		final String inviteId;
		final String toUuid;
		final String toUsername;
		final long expiresAt;

		InviteSentAck(String inviteId, String toUuid, String toUsername, long expiresAt) {
			this.inviteId = inviteId;
			this.toUuid = toUuid;
			this.toUsername = toUsername;
			this.expiresAt = expiresAt;
		}
	}

	static final class InviteResult {
		final String type = "invite_result";
		final String inviteId;
		final String targetUuid;
		final String targetUsername;
		final boolean accepted;
		final String reason;

		InviteResult(String inviteId, String targetUuid, String targetUsername, boolean accepted, String reason) {
			this.inviteId = inviteId;
			this.targetUuid = targetUuid;
			this.targetUsername = targetUsername;
			this.accepted = accepted;
			this.reason = reason;
		}
	}

	static final class MemberInfo {
		final String uuid;
		final String username;
		final boolean online;

		MemberInfo(String uuid, String username, boolean online) {
			this.uuid = uuid;
			this.username = username;
			this.online = online;
		}
	}

	static final class PartyUpdate {
		final String type = "party_update";
		final String leaderUuid;
		final List<MemberInfo> members;

		PartyUpdate(String leaderUuid, List<MemberInfo> members) {
			this.leaderUuid = leaderUuid;
			this.members = members;
		}
	}

	static final class LobbySeenOut {
		final String type = "lobby_seen";
		final String fromUuid;
		final String lobbyId;

		LobbySeenOut(String fromUuid, String lobbyId) {
			this.fromUuid = fromUuid;
			this.lobbyId = lobbyId;
		}
	}

	static final class Kicked {
		final String type = "kicked";
		final String byUuid;

		Kicked(String byUuid) {
			this.byUuid = byUuid;
		}
	}
}
