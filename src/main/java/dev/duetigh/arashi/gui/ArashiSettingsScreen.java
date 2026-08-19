package dev.duetigh.arashi.gui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.config.EspMode;

/** ESP appearance (mode, outline/fill RGB color, width, opacity) and the chat-coordinates toggle. */
public final class ArashiSettingsScreen extends Screen {
	private static final int WIDGET_WIDTH = 200;
	private static final int WIDGET_HEIGHT = 20;
	private static final int SPACING = 24;
	private static final int SLIDER_WIDTH = 160;
	private static final int SWATCH_GAP = 4;
	private static final int SWATCH_WIDTH = WIDGET_WIDTH - SLIDER_WIDTH - SWATCH_GAP;
	private static final float MIN_OUTLINE_WIDTH = 1.0f;
	private static final float MAX_OUTLINE_WIDTH = 8.0f;

	private final ArashiConfig config;

	private int outlineSwatchX;
	private int outlineSwatchY;
	private int fillSwatchX;
	private int fillSwatchY;
	private int swatchHeight;

	public ArashiSettingsScreen(ArashiConfig config) {
		super(Component.literal("Arashi - Settings"));
		this.config = config;
	}

	@Override
	protected void init() {
		int x = this.width / 2 - WIDGET_WIDTH / 2;
		int y = 32;
		swatchHeight = SPACING * 3 - SWATCH_GAP;

		this.addRenderableWidget(CycleButton.builder((EspMode mode) -> Component.literal("ESP Mode: " + modeLabel(mode)), config.espMode())
				.withValues(EspMode.values())
				.displayOnlyValue()
				.create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), (button, mode) -> config.setEspMode(mode)));
		y += SPACING;

		outlineSwatchX = x + SLIDER_WIDTH + SWATCH_GAP;
		outlineSwatchY = y;
		y = addColorSliders(x, y, "Outline", config::outlineColor, config::setOutlineColor);

		this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, widthToSlider(config.outlineWidth()),
				value -> Component.literal("Outline Width: " + String.format("%.1f", sliderToWidth(value)) + "px"),
				value -> config.setOutlineWidth((float) sliderToWidth(value))));
		y += SPACING;

		this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, config.outlineOpacity(),
				value -> Component.literal("Outline Opacity: " + Math.round(value * 100) + "%"),
				value -> config.setOutlineOpacity((float) value)));
		y += SPACING;

		fillSwatchX = x + SLIDER_WIDTH + SWATCH_GAP;
		fillSwatchY = y;
		y = addColorSliders(x, y, "Fill", config::fillColor, config::setFillColor);

		this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, config.fillOpacity(),
				value -> Component.literal("Fill Opacity: " + Math.round(value * 100) + "%"),
				value -> config.setFillOpacity((float) value)));
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

	/** Adds R/G/B sliders (0-255) for a packed 0xRRGGBB color, returning the y position after them. */
	private int addColorSliders(int x, int y, String label, IntSupplier colorGetter, IntConsumer colorSetter) {
		int[] shifts = {16, 8, 0};
		String[] channelNames = {"R", "G", "B"};

		for (int i = 0; i < shifts.length; i++) {
			int shift = shifts[i];
			String channelLabel = label + " " + channelNames[i];

			this.addRenderableWidget(new ValueSlider(x, y, SLIDER_WIDTH, WIDGET_HEIGHT, ((colorGetter.getAsInt() >> shift) & 0xFF) / 255.0,
					value -> Component.literal(channelLabel + ": " + Math.round(value * 255)),
					value -> colorSetter.accept(withChannel(colorGetter.getAsInt(), shift, (int) Math.round(value * 255)))));
			y += SPACING;
		}

		return y;
	}

	private static int withChannel(int rgb, int shift, int value) {
		return (rgb & ~(0xFF << shift)) | ((value & 0xFF) << shift);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawSwatch(graphics, outlineSwatchX, outlineSwatchY, config.outlineColor());
		drawSwatch(graphics, fillSwatchX, fillSwatchY, config.fillColor());
	}

	private void drawSwatch(GuiGraphicsExtractor graphics, int x, int y, int rgb) {
		graphics.fill(x - 1, y - 1, x + SWATCH_WIDTH + 1, y + swatchHeight + 1, 0xFFFFFFFF);
		graphics.fill(x, y, x + SWATCH_WIDTH, y + swatchHeight, 0xFF000000 | rgb);
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
