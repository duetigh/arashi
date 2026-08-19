package dev.duetigh.arashi.party.server;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A multi-member party. Solo players have no {@link Party} instance - see {@link PartyRegistry}. */
final class Party {
	UUID leaderUuid;
	final Set<UUID> members = new LinkedHashSet<>();

	Party(UUID leaderUuid) {
		this.leaderUuid = leaderUuid;
		members.add(leaderUuid);
	}
}
