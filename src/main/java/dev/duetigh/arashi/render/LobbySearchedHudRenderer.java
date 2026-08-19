package dev.duetigh.arashi.render;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import dev.duetigh.arashi.ArashiClient;
import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.hypixel.LobbyTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/** Big centered rainbow "LOBBY SEARCHED" banner shown for a few seconds when rejoining a previously-visited Hypixel lobby. */
public final class LobbySearchedHudRenderer {
	private static final String TEXT = "LOBBY SEARCHED";
	private static final float SCALE = 3.0f;
	private static final float HUE_CYCLE_MS = 3000f;
	private static final float HUE_SHIFT_PER_CHAR = 0.06f;

	private final ArashiConfig config;
	private final LobbyTracker lobbyTracker;

	public LobbySearchedHudRenderer(ArashiConfig config, LobbyTracker lobbyTracker) {
		this.config = config;
		this.lobbyTracker = lobbyTracker;
	}

	public void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ArashiClient.MOD_ID, "lobby_searched"), this::extractRenderState);
	}

	private void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!config.lobbySearchedTextEnabled() || !lobbyTracker.isShowingLobbySearched()) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		float alpha = lobbyTracker.fadeAlpha();
		int textWidth = font.width(TEXT);
		float x = (graphics.guiWidth() - textWidth * SCALE) / 2f;
		float y = graphics.guiHeight() / 3f;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(SCALE, SCALE);

		float cursorX = 0f;

		for (int i = 0; i < TEXT.length(); i++) {
			String ch = String.valueOf(TEXT.charAt(i));
			graphics.text(font, ch, Math.round(cursorX), 0, hueColor(i, alpha), true);
			cursorX += font.width(ch);
		}

		pose.popMatrix();
	}

	private static int hueColor(int index, float alpha) {
		float hue = (System.currentTimeMillis() % (long) HUE_CYCLE_MS) / HUE_CYCLE_MS + index * HUE_SHIFT_PER_CHAR;
		int rgb = java.awt.Color.HSBtoRGB(hue % 1f, 1f, 1f) & 0xFFFFFF;
		return (Math.round(alpha * 255) << 24) | rgb;
	}
}
