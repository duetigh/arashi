package dev.duetigh.arashi.gui;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.scanner.BlockScanner;

/** Minimal, searchable multi-select list of every registered block, backed by {@link ArashiConfig}. */
public final class BlockSelectScreen extends Screen {
	private final ArashiConfig config;
	private final BlockScanner scanner;
	private final List<Identifier> allBlockIds;

	private EditBox searchBox;
	private BlockList list;

	public BlockSelectScreen(ArashiConfig config, BlockScanner scanner) {
		super(Component.literal("Arashi - Block Scanner"));
		this.config = config;
		this.scanner = scanner;
		this.allBlockIds = BuiltInRegistries.BLOCK.stream()
				.map(BuiltInRegistries.BLOCK::getKey)
				.sorted()
				.toList();
	}

	@Override
	protected void init() {
		this.searchBox = this.addRenderableWidget(new EditBox(this.font, this.width / 2 - 100, 24, 200, 20, Component.literal("Search")));
		this.searchBox.setResponder(query -> refresh(query));

		this.list = this.addRenderableOnly(new BlockList(this.minecraft, this.width, this.height - 80, 52, 20));
		this.addWidget(this.list);

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.pos(this.width / 2 - 50, this.height - 26)
				.size(100, 20)
				.build());

		refresh("");
	}

	private void refresh(String query) {
		this.list.clear();
		String needle = query.strip().toLowerCase();

		for (Identifier id : allBlockIds) {
			if (needle.isEmpty() || id.toString().contains(needle)) {
				this.list.addEntry(this.list.new BlockEntry(id));
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 6, 0xFFFFFF, true);
	}

	@Override
	public void onClose() {
		config.save();
		scanner.setTrackedBlockIds(config.trackedBlockIds());
		super.onClose();
	}

	private final class BlockList extends AbstractSelectionList<BlockList.BlockEntry> {
		BlockList(Minecraft client, int width, int height, int top, int itemHeight) {
			super(client, width, height, top, itemHeight);
		}

		@Override
		public int addEntry(BlockEntry entry) {
			return super.addEntry(entry);
		}

		void clear() {
			this.clearEntries();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}

		final class BlockEntry extends AbstractSelectionList.Entry<BlockEntry> {
			private final Identifier blockId;
			private final Block block;

			BlockEntry(Identifier blockId) {
				this.blockId = blockId;
				this.block = BuiltInRegistries.BLOCK.getValue(blockId);
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
				boolean tracked = config.trackedBlockIds().contains(blockId.toString());
				String checkbox = tracked ? "[x]" : "[ ]";
				Component line = Component.literal(checkbox + " " + blockId)
						.withStyle(tracked ? ChatFormatting.GREEN : ChatFormatting.GRAY);
				graphics.text(BlockSelectScreen.this.font, line, getContentX() + 4, getContentY() + 4, 0xFFFFFF, false);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				if (block == null) {
					return false;
				}

				config.toggle(blockId.toString());
				return true;
			}
		}
	}
}
