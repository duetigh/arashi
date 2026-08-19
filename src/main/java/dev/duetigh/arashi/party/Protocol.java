package dev.duetigh.arashi.party;

import java.util.List;

/**
 * Gson DTOs for the party relay's JSON-over-WebSocket protocol, one object per text frame,
 * discriminated by a "type" field. Hand-mirrored from the relay server's copy
 * (server/src/main/java/dev/duetigh/arashi/party/server/Protocol.java) rather than shared, since
 * the two are intentionally separate Gradle builds; {@link #PROTOCOL_VERSION} guards against the
 * two copies drifting silently.
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
		final String type = "hello";
		final int protocolVersion = PROTOCOL_VERSION;
		final String uuid;
		final String username;
		final String clientVersion;

		Hello(String uuid, String username, String clientVersion) {
			this.uuid = uuid;
			this.username = username;
			this.clientVersion = clientVersion;
		}
	}

	static final class Invite {
		final String type = "invite";
		final String targetUsername;

		Invite(String targetUsername) {
			this.targetUsername = targetUsername;
		}
	}

	static final class InviteResponse {
		final String type = "invite_response";
		final String inviteId;
		final boolean accept;

		InviteResponse(String inviteId, boolean accept) {
			this.inviteId = inviteId;
			this.accept = accept;
		}
	}

	static final class Kick {
		final String type = "kick";
		final String targetUuid;

		Kick(String targetUuid) {
			this.targetUuid = targetUuid;
		}
	}

	static final class Leave {
		final String type = "leave";
	}

	static final class LobbySeenIn {
		final String type = "lobby_seen";
		final String lobbyId;

		LobbySeenIn(String lobbyId) {
			this.lobbyId = lobbyId;
		}
	}

	// ---- Server -> Client ----

	static final class HelloAck {
		String uuid;
	}

	static final class Error {
		String code;
		String message;
	}

	static final class InviteReceived {
		String inviteId;
		String fromUuid;
		String fromUsername;
		long expiresAt;
	}

	static final class InviteSentAck {
		String inviteId;
		String toUuid;
		String toUsername;
		long expiresAt;
	}

	static final class InviteResult {
		String inviteId;
		String targetUuid;
		String targetUsername;
		boolean accepted;
		String reason;
	}

	static final class MemberInfo {
		String uuid;
		String username;
		boolean online;
	}

	static final class PartyUpdate {
		String leaderUuid;
		List<MemberInfo> members;
	}

	static final class LobbySeenOut {
		String fromUuid;
		String lobbyId;
	}

	static final class Kicked {
		String byUuid;
	}
}
