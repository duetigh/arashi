package dev.duetigh.arashi.gui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.config.EspMode;

/** ESP appearance (mode, outline color/width, fill/outline opacity) and the chat-coordinates toggle. */
public final class ArashiSettingsScreen extends Screen {
	private static final int WIDGET_WIDTH = 200;
	private static final int WIDGET_HEIGHT = 20;
	private static final int SPACING = 24;
	private static final float MIN_OUTLINE_WIDTH = 1.0f;
	private static final float MAX_OUTLINE_WIDTH = 8.0f;

	private final ArashiConfig config;

	public ArashiSettingsScreen(ArashiConfig config) {
		super(Component.literal("Arashi - Settings"));
		this.config = config;
	}

	@Override
	protected void init() {
		int x = this.width / 2 - WIDGET_WIDTH / 2;
		int y = 32;

		this.addRenderableWidget(CycleButton.builder((EspMode mode) -> Component.literal("ESP Mode: " + modeLabel(mode)), config.espMode())
				.withValues(EspMode.values())
				.displayOnlyValue()
				.create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), (button, mode) -> config.setEspMode(mode)));
		y += SPACING;

		this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, config.outlineHue(),
				value -> Component.literal("Outline Color: " + Math.round(value * 360) + "°"),
				value -> config.setOutlineHue((float) value)));
		y += SPACING;

		this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, widthToSlider(config.outlineWidth()),
				value -> Component.literal("Outline Width: " + String.format("%.1f", sliderToWidth(value)) + "px"),
				value -> config.setOutlineWidth((float) sliderToWidth(value))));
		y += SPACING;

		this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, config.fillOpacity(),
				value -> Component.literal("Overlay Opacity: " + Math.round(value * 100) + "%"),
				value -> config.setFillOpacity((float) value)));
		y += SPACING;

		this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, config.outlineOpacity(),
				value -> Component.literal("Outline Opacity: " + Math.round(value * 100) + "%"),
				value -> config.setOutlineOpacity((float) value)));
		y += SPACING;

		this.addRenderableWidget(CycleButton.onOffBuilder(config.chatCoordsEnabled())
				.create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("Chat Coordinates"),
						(button, enabled) -> config.setChatCoordsEnabled(enabled)));
		y += SPACING;

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.pos(x, y)
				.size(WIDGET_WIDTH, WIDGET_HEIGHT)
				.build());
	}

	@Override
	public void onClose() {
		config.save();
		super.onClose();
	}

	private static String modeLabel(EspMode mode) {
		return switch (mode) {
			case OVERLAY -> "Overlay";
			case OUTLINE -> "Outline";
			case BOTH -> "Both";
		};
	}

	private static double widthToSlider(float width) {
		return (width - MIN_OUTLINE_WIDTH) / (MAX_OUTLINE_WIDTH - MIN_OUTLINE_WIDTH);
	}

	private static double sliderToWidth(double value) {
		return MIN_OUTLINE_WIDTH + value * (MAX_OUTLINE_WIDTH - MIN_OUTLINE_WIDTH);
	}

	/** A slider whose 0-1 value is mapped to a display label and a config field by the caller. */
	private static final class ValueSlider extends AbstractSliderButton {
		private final DoubleFunction<Component> labelFactory;
		private final DoubleConsumer onChange;

		ValueSlider(int x, int y, int width, int height, double initialValue, DoubleFunction<Component> labelFactory, DoubleConsumer onChange) {
			super(x, y, width, height, Component.empty(), initialValue);
			this.labelFactory = labelFactory;
			this.onChange = onChange;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(labelFactory.apply(value));
		}

		@Override
		protected void applyValue() {
			onChange.accept(value);
		}
	}
}
