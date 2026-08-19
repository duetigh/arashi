package dev.duetigh.arashi.party;

import java.util.UUID;

public record PartyMember(UUID uuid, String username, boolean online) {
}
