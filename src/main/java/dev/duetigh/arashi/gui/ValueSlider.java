package dev.duetigh.arashi.gui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider whose 0-1 value is mapped to a display label and a config field by the caller. */
final class ValueSlider extends AbstractSliderButton {
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
