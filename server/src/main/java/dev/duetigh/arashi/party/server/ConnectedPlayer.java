package dev.duetigh.arashi.party.server;

import java.util.UUID;

import org.java_websocket.WebSocket;

/** A currently-connected client, identified by its self-reported Mojang UUID/username. */
final class ConnectedPlayer {
	final UUID uuid;
	final String username;
	final WebSocket socket;

	ConnectedPlayer(UUID uuid, String username, WebSocket socket) {
		this.uuid = uuid;
		this.username = username;
		this.socket = socket;
	}
}
