package dev.duetigh.arashi.hypixel;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Reads Hypixel's "Area"/"Server" lines from the scoreboard sidebar. Hypixel (like other legacy-
 * scoreboard-protocol servers) never sets {@link PlayerScoreEntry#display()} - that field only
 * overrides the fake score-holder name, and Hypixel instead builds each visible line out of the
 * player team's prefix/suffix wrapped around that fake name, exactly like vanilla's own sidebar
 * renderer does (see {@code Gui#displayScoreboardSidebar}) and the same way other Hypixel-aware
 * mods such as SkyHanni read it. So the line text has to be reconstructed the same way, not read
 * off {@code display()} directly.
 */
public final class HypixelWidgetReader {
	private static final Pattern LABEL_VALUE = Pattern.compile("^([A-Za-z ]+):\\s*(.+)$");
	private static final String CRYSTAL_HOLLOWS = "Crystal Hollows";
	private static final int STABLE_READS_REQUIRED = 3;

	private volatile String currentArea;
	private volatile String currentServerInstance;
	private volatile boolean stableInCrystalHollows;
	private int consecutiveMatchingReads;

	public void tick(ClientLevel level) {
		Scoreboard scoreboard = level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

		if (sidebar == null) {
			currentArea = null;
			currentServerInstance = null;
			updateStability(false);
			return;
		}

		String area = null;
		String serverInstance = null;

		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
			if (entry.isHidden()) {
				continue;
			}

			PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
			MutableComponent name = PlayerTeam.formatNameForTeam(team, entry.ownerName());
			String line = name.getString().strip();
			Matcher matcher = LABEL_VALUE.matcher(line);

			if (!matcher.matches()) {
				continue;
			}

			String label = matcher.group(1).strip();
			String value = matcher.group(2).strip();

			if (label.equalsIgnoreCase("Area")) {
				area = value;
			} else if (label.equalsIgnoreCase("Server")) {
				serverInstance = value;
			}
		}

		currentArea = area;
		currentServerInstance = serverInstance;
		updateStability(area != null && area.equalsIgnoreCase(CRYSTAL_HOLLOWS));
	}

	private void updateStability(boolean matchedThisRead) {
		if (matchedThisRead) {
			consecutiveMatchingReads = Math.min(consecutiveMatchingReads + 1, STABLE_READS_REQUIRED);
		} else {
			consecutiveMatchingReads = 0;
		}

		stableInCrystalHollows = consecutiveMatchingReads >= STABLE_READS_REQUIRED;
	}

	public boolean isConnectedToHypixel() {
		ServerData server = Minecraft.getInstance().getCurrentServer();
		return server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("hypixel");
	}

	public boolean isInCrystalHollowsStable() {
		return stableInCrystalHollows;
	}

	public Optional<String> lobbyId() {
		return Optional.ofNullable(currentServerInstance);
	}
}
