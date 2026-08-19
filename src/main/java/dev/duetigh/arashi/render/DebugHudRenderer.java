package dev.duetigh.arashi.render;

import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

import dev.duetigh.arashi.ArashiClient;
import dev.duetigh.arashi.scanner.BlockScanner;
import dev.duetigh.arashi.util.BlockDisplay;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/** While {@link BlockScanner#isDebugMode()} is on, lists tracked-block counts per loaded chunk in the top-left corner. */
public final class DebugHudRenderer {
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
		int x = 4;
		int y = 4;

		graphics.text(font, Component.literal("Arashi Debug").withStyle(ChatFormatting.YELLOW), x, y, 0xFFFFFFFF, true);
		y += font.lineHeight + 2;

		for (Map.Entry<ChunkPos, Map<Block, Integer>> chunkEntry : counts.entrySet()) {
			ChunkPos pos = chunkEntry.getKey();
			graphics.text(font, Component.literal("Chunk (" + pos.x() + ", " + pos.z() + ")").withStyle(ChatFormatting.AQUA), x, y, 0xFFFFFFFF, true);
			y += font.lineHeight;

			for (Map.Entry<Block, Integer> blockEntry : chunkEntry.getValue().entrySet()) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(blockEntry.getKey());
				String line = "  " + BlockDisplay.shortName(id) + ": " + blockEntry.getValue();
				graphics.text(font, Component.literal(line), x, y, 0xFFFFFFFF, true);
				y += font.lineHeight;
			}
		}
	}
}
