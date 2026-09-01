package dev.duetigh.arashi.gui.widget;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import dev.duetigh.arashi.gui.theme.ArashiTheme;

/** A single-line row with a leading label and a trailing detail (e.g. a name and its distance). */
public class ArashiListRow extends ArashiWidget {
	private final String leading;
	private String trailing;
	private final Consumer<ArashiListRow> onClick;

	public ArashiListRow(String leading, String trailing, Consumer<ArashiListRow> onClick) {
		this.leading = leading;
		this.trailing = trailing;
		this.onClick = onClick;
	}

	public void setTrailing(String trailing) {
		this.trailing = trailing;
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
		if (hover.get() > 0.01f) {
			int fill = ArashiButton.lerpColor(0x00000000, ArashiTheme.CARD_HOVER, hover.get());
			ctx.fill(x, y, x + width, y + height, fill);
		}

		var font = Minecraft.getInstance().font;
		int textY = y + (height - font.lineHeight) / 2;
		ctx.text(font, leading, x + ArashiTheme.PADDING, textY, ArashiTheme.TEXT_PRIMARY);

		if (trailing != null) {
			int trailingWidth = font.width(trailing);
			ctx.text(font, trailing, x + width - ArashiTheme.PADDING - trailingWidth, textY, ArashiTheme.TEXT_SECONDARY);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, MouseButtonEvent event) {
		boolean handled = super.mouseClicked(mouseX, mouseY, event);

		if (handled && onClick != null) {
			onClick.accept(this);
		}

		return handled;
	}
}
