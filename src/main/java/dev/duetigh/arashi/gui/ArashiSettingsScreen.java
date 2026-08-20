package dev.duetigh.arashi.gui;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.config.EspMode;

/** ESP appearance (mode, width, opacity), keybind rebinding, and other toggles. Per-block overlay/outline colors are set from the block scanner screen. */
public final class ArashiSettingsScreen extends Screen {
	private static final int WIDGET_WIDTH = 200;
	private static final int WIDGET_HEIGHT = 20;
	private static final int SPACING = 24;
	private static final float MIN_OUTLINE_WIDTH = 1.0f;
	private static final float MAX_OUTLINE_WIDTH = 8.0f;

	private final ArashiConfig config;
	private final KeyMapping openScannerKey;
	private final KeyMapping toggleEspKey;
	private final KeyMapping toggleScanKey;
	private final KeyMapping openScanBrowserKey;

	private Button openScannerKeyButton;
	private Button toggleEspKeyButton;
	private Button toggleScanKeyButton;
	private Button openScanBrowserKeyButton;
	private KeyMapping listeningFor;

	public ArashiSettingsScreen(ArashiConfig config, KeyMapping openScannerKey, KeyMapping toggleEspKey,
			KeyMapping toggleScanKey, KeyMapping openScanBrowserKey) {
		super(Component.literal("Arashi - Settings"));
		this.config = config;
		this.openScannerKey = openScannerKey;
		this.toggleEspKey = toggleEspKey;
		this.toggleScanKey = toggleScanKey;
		this.openScanBrowserKey = openScanBrowserKey;
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

		this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, widthToSlider(config.outlineWidth()),
				value -> Component.literal("Outline Width: " + String.format("%.1f", sliderToWidth(value)) + "px"),
				value -> config.setOutlineWidth((float) sliderToWidth(value))));
		y += SPACING;

		this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, config.outlineOpacity(),
				value -> Component.literal("Outline Opacity: " + Math.round(value * 100) + "%"),
				value -> config.setOutlineOpacity((float) value)));
		y += SPACING;

		this.addRenderableWidget(new ValueSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, config.fillOpacity(),
				value -> Component.literal("Fill Opacity: " + Math.round(value * 100) + "%"),
				value -> config.setFillOpacity((float) value)));
		y += SPACING;

		this.addRenderableWidget(CycleButton.onOffBuilder(config.chatCoordsEnabled())
				.create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("Chat Coordinates"),
						(button, enabled) -> config.setChatCoordsEnabled(enabled)));
		y += SPACING;

		this.addRenderableWidget(CycleButton.onOffBuilder(config.restrictToCrystalHollows())
				.create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("Restrict to Crystal Hollows"),
						(button, enabled) -> config.setRestrictToCrystalHollows(enabled)));
		y += SPACING;

		this.addRenderableWidget(CycleButton.onOffBuilder(config.lobbySearchedTextEnabled())
				.create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("Lobby Searched Text"),
						(button, enabled) -> config.setLobbySearchedTextEnabled(enabled)));
		y += SPACING;

		openScannerKeyButton = this.addRenderableWidget(Button.builder(keyLabel("Open GUI", openScannerKey), b -> startListening(openScannerKey))
				.pos(x, y)
				.size(WIDGET_WIDTH, WIDGET_HEIGHT)
				.build());
		y += SPACING;

		toggleEspKeyButton = this.addRenderableWidget(Button.builder(keyLabel("Toggle ESP", toggleEspKey), b -> startListening(toggleEspKey))
				.pos(x, y)
				.size(WIDGET_WIDTH, WIDGET_HEIGHT)
				.build());
		y += SPACING;

		toggleScanKeyButton = this.addRenderableWidget(Button.builder(keyLabel("Toggle Scan", toggleScanKey), b -> startListening(toggleScanKey))
				.pos(x, y)
				.size(WIDGET_WIDTH, WIDGET_HEIGHT)
				.build());
		y += SPACING;

		openScanBrowserKeyButton = this.addRenderableWidget(Button.builder(keyLabel("Open Scans", openScanBrowserKey), b -> startListening(openScanBrowserKey))
				.pos(x, y)
				.size(WIDGET_WIDTH, WIDGET_HEIGHT)
				.build());
		y += SPACING;

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.pos(x, y)
				.size(WIDGET_WIDTH, WIDGET_HEIGHT)
				.build());
	}

	private void startListening(KeyMapping mapping) {
		listeningFor = mapping;
		refreshKeyLabels();
	}

	private void refreshKeyLabels() {
		openScannerKeyButton.setMessage(keyLabel("Open GUI", openScannerKey));
		toggleEspKeyButton.setMessage(keyLabel("Toggle ESP", toggleEspKey));
		toggleScanKeyButton.setMessage(keyLabel("Toggle Scan", toggleScanKey));
		openScanBrowserKeyButton.setMessage(keyLabel("Open Scans", openScanBrowserKey));
	}

	private Component keyLabel(String action, KeyMapping mapping) {
		if (mapping == listeningFor) {
			return Component.literal(action + ": > Press a key, Esc to cancel <");
		}

		return Component.literal(action + ": ").append(mapping.getTranslatedKeyMessage());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listeningFor != null) {
			if (event.key() != GLFW.GLFW_KEY_ESCAPE) {
				if (event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_DELETE) {
					listeningFor.setKey(InputConstants.UNKNOWN);
				} else {
					listeningFor.setKey(InputConstants.getKey(event));
				}

				KeyMapping.resetMapping();
				Minecraft.getInstance().options.save();
			}

			listeningFor = null;
			refreshKeyLabels();
			return true;
		}

		return super.keyPressed(event);
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
			case TEXTURE -> "Texture";
		};
	}

	private static double widthToSlider(float width) {
		return (width - MIN_OUTLINE_WIDTH) / (MAX_OUTLINE_WIDTH - MIN_OUTLINE_WIDTH);
	}

	private static double sliderToWidth(double value) {
		return MIN_OUTLINE_WIDTH + value * (MAX_OUTLINE_WIDTH - MIN_OUTLINE_WIDTH);
	}
}
