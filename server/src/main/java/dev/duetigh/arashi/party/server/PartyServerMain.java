package dev.duetigh.arashi.party.server;

/** Entrypoint for the standalone party relay process (see {@code server/README.md} for deployment). */
public final class PartyServerMain {
	private static final int DEFAULT_PORT = 8887;

	private PartyServerMain() {
	}

	public static void main(String[] args) {
		PartyServer server = new PartyServer(resolvePort());

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				server.shutdown();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}, "arashi-party-server-shutdown"));

		server.start();
	}

	private static int resolvePort() {
		String env = System.getenv("ARASHI_PARTY_PORT");
		if (env == null) {
			return DEFAULT_PORT;
		}

		try {
			return Integer.parseInt(env.trim());
		} catch (NumberFormatException e) {
			return DEFAULT_PORT;
		}
	}
}
