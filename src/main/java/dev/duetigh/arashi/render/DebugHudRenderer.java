package dev.duetigh.arashi.render;

import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

import dev.duetigh.arashi.ArashiClient;
import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.theme.RoundedRectRenderer;
import dev.duetigh.arashi.scanner.BlockScanner;
import dev.duetigh.arashi.util.BlockDisplay;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/** While {@link BlockScanner#isDebugMode()} is on, lists tracked-block counts per loaded chunk in a themed card in the top-left corner. */
public final class DebugHudRenderer {
	private static final int MARGIN = 6;
	private static final int CARD_WIDTH = 200;

	private final BlockScanner scanner;

	public DebugHudRenderer(BlockScanner scanner) {
		this.scanner = scanner;
	}

	public void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ArashiClient.MOD_ID, "debug_hud"), this::extractRenderState);
	}

	private void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!scanner.isDebugMode()) {
			return;
		}

		Map<ChunkPos, Map<Block, Integer>> counts = scanner.debugCounts();

		if (counts.isEmpty()) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		int lineCount = 1;

		for (Map<Block, Integer> chunkCounts : counts.values()) {
			lineCount += 1 + chunkCounts.size();
		}

		int cardHeight = lineCount * font.lineHeight + MARGIN * 2;
		RoundedRectRenderer.fill(graphics, MARGIN, MARGIN, CARD_WIDTH, cardHeight, ArashiTheme.PANEL, ArashiTheme.BACKGROUND);

		int x = MARGIN * 2;
		int y = MARGIN + MARGIN / 2;

		graphics.text(font, "Arashi Debug", x, y, ArashiTheme.ACCENT, false);
		y += font.lineHeight;

		for (Map.Entry<ChunkPos, Map<Block, Integer>> chunkEntry : counts.entrySet()) {
			ChunkPos pos = chunkEntry.getKey();
			graphics.text(font, "Chunk (" + pos.x() + ", " + pos.z() + ")", x, y, ArashiTheme.TEXT_SECONDARY, false);
			y += font.lineHeight;

			for (Map.Entry<Block, Integer> blockEntry : chunkEntry.getValue().entrySet()) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(blockEntry.getKey());
				String line = "  " + BlockDisplay.shortName(id) + ": " + blockEntry.getValue();
				graphics.text(font, line, x, y, ArashiTheme.TEXT_PRIMARY, false);
				y += font.lineHeight;
			}
		}
	}
}
