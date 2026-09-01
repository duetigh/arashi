package dev.duetigh.arashi.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.config.EspMode;
import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.widget.ArashiButton;
import dev.duetigh.arashi.gui.widget.ArashiScrollPanel;
import dev.duetigh.arashi.gui.widget.ArashiSlider;
import dev.duetigh.arashi.gui.widget.ArashiTabBar;
import dev.duetigh.arashi.gui.widget.ArashiToggleRow;

/**
 * ESP appearance (mode, width, opacity), automation toggles, waypoint colors, and keybind
 * rebinding, all inside one scrollable panel. Per-block overlay/outline colors are set from the
 * block scanner screen.
 */
public final class ArashiSettingsScreen extends ArashiScreen {
	private static final int WIDGET_WIDTH = 150;
	private static final int WIDGET_HEIGHT = 20;
	private static final float MIN_OUTLINE_WIDTH = 1.0f;
	private static final float MAX_OUTLINE_WIDTH = 8.0f;

	private final ArashiConfig config;
	private final KeyMapping openScannerKey;
	private final KeyMapping toggleEspKey;
	private final KeyMapping toggleScanKey;
	private final KeyMapping openScanBrowserKey;
	private final KeyMapping copyLastCoordsKey;

	private KeyMapping listeningFor;

	public ArashiSettingsScreen(ArashiConfig config, KeyMapping openScannerKey, KeyMapping toggleEspKey,
			KeyMapping toggleScanKey, KeyMapping openScanBrowserKey, KeyMapping copyLastCoordsKey) {
		super(Component.literal("Arashi - Settings"), null);
		this.config = config;
		this.openScannerKey = openScannerKey;
		this.toggleEspKey = toggleEspKey;
		this.toggleScanKey = toggleScanKey;
		this.openScanBrowserKey = openScanBrowserKey;
		this.copyLastCoordsKey = copyLastCoordsKey;
	}

	@Override
	protected void buildWidgets() {
		int panelX = this.width / 2 - WIDGET_WIDTH / 2;
		int panelTop = 30;
		int gap = ArashiTheme.GAP;

		ArashiScrollPanel panel = track(new ArashiScrollPanel());
		panel.setBounds(panelX, panelTop, WIDGET_WIDTH, this.height - panelTop - ArashiTheme.PADDING);

		int y = 0;

		ArashiTabBar espModeTabs = new ArashiTabBar(List.of("Overlay", "Outline", "Both", "Texture"), config.espMode().ordinal(),
				index -> config.setEspMode(EspMode.values()[index]));
		espModeTabs.setSize(WIDGET_WIDTH, WIDGET_HEIGHT);
		panel.add(espModeTabs, 0, y);
		y += WIDGET_HEIGHT + gap;

		ArashiSlider outlineWidthSlider = new ArashiSlider(widthToSlider(config.outlineWidth()),
				value -> "Outline Width: " + String.format("%.1f", sliderToWidth(value)) + "px",
				value -> config.setOutlineWidth((float) sliderToWidth(value)));
		outlineWidthSlider.setSize(WIDGET_WIDTH, WIDGET_HEIGHT);
		panel.add(outlineWidthSlider, 0, y);
		y += WIDGET_HEIGHT + gap;

		ArashiSlider outlineOpacitySlider = new ArashiSlider(config.outlineOpacity(),
				value -> "Outline Opacity: " + Math.round(value * 100) + "%",
				value -> config.setOutlineOpacity((float) value));
		outlineOpacitySlider.setSize(WIDGET_WIDTH, WIDGET_HEIGHT);
		panel.add(outlineOpacitySlider, 0, y);
		y += WIDGET_HEIGHT + gap;

		ArashiSlider fillOpacitySlider = new ArashiSlider(config.fillOpacity(),
				value -> "Fill Opacity: " + Math.round(value * 100) + "%",
				value -> config.setFillOpacity((float) value));
		fillOpacitySlider.setSize(WIDGET_WIDTH, WIDGET_HEIGHT);
		panel.add(fillOpacitySlider, 0, y);
		y += WIDGET_HEIGHT + gap;

		y = addToggleRow(panel, y, "Chat Coordinates", config.chatCoordsEnabled(), config::setChatCoordsEnabled);
		y = addToggleRow(panel, y, "Restrict to Crystal Hollows", config.restrictToCrystalHollows(), config::setRestrictToCrystalHollows);
		y = addToggleRow(panel, y, "Lobby Searched Text", config.lobbySearchedTextEnabled(), config::setLobbySearchedTextEnabled);
		y = addToggleRow(panel, y, "Tracking Mode Box ESP", config.trackingShowBoxEsp(), config::setTrackingShowBoxEsp);
		y = addToggleRow(panel, y, "Auto Chest", config.autoChestEnabled(), config::setAutoChestEnabled);
		y = addToggleRow(panel, y, "Waypoint Floating Text", config.waypointFloatingTextEnabled(), config::setWaypointFloatingTextEnabled);

		y = addColorSliders(panel, y, "Pickobulus Color", config::waypointPickobulusColor, config::setWaypointPickobulusColor);
		y = addColorSliders(panel, y, "Etherwarp Color", config::waypointEtherwarpColor, config::setWaypointEtherwarpColor);

		y = addKeybindRow(panel, y, "Open GUI", openScannerKey);
		y = addKeybindRow(panel, y, "Toggle ESP", toggleEspKey);
		y = addKeybindRow(panel, y, "Toggle Scan", toggleScanKey);
		y = addKeybindRow(panel, y, "Open Scans", openScanBrowserKey);
		y = addKeybindRow(panel, y, "Copy Last Coords", copyLastCoordsKey);

		ArashiButton doneButton = new ArashiButton("Done", ArashiButton.Style.SECONDARY, b -> onClose());
		doneButton.setSize(WIDGET_WIDTH, WIDGET_HEIGHT);
		panel.add(doneButton, 0, y);
	}

	private int addToggleRow(ArashiScrollPanel panel, int y, String label, boolean initial, Consumer<Boolean> onChange) {
		ArashiToggleRow row = new ArashiToggleRow(label, initial, onChange);
		row.setSize(WIDGET_WIDTH, WIDGET_HEIGHT);
		panel.add(row, 0, y);
		return y + WIDGET_HEIGHT + ArashiTheme.GAP;
	}

	/** Adds R/G/B sliders (0-255) for a packed 0xRRGGBB color, returning the y position after them. */
	private int addColorSliders(ArashiScrollPanel panel, int y, String label, IntSupplier colorGetter, IntConsumer colorSetter) {
		int[] shifts = {16, 8, 0};
		String[] channelNames = {"R", "G", "B"};

		for (int i = 0; i < shifts.length; i++) {
			int shift = shifts[i];
			String channelLabel = label + " " + channelNames[i];

			ArashiSlider slider = new ArashiSlider(((colorGetter.getAsInt() >> shift) & 0xFF) / 255.0,
					value -> channelLabel + ": " + Math.round(value * 255),
					value -> colorSetter.accept(withChannel(colorGetter.getAsInt(), shift, (int) Math.round(value * 255))));
			slider.setSize(WIDGET_WIDTH, WIDGET_HEIGHT);
			panel.add(slider, 0, y);
			y += WIDGET_HEIGHT + ArashiTheme.GAP;
		}

		return y;
	}

	private int addKeybindRow(ArashiScrollPanel panel, int y, String action, KeyMapping mapping) {
		ArashiButton button = new ArashiButton(() -> keyLabel(action, mapping), ArashiButton.Style.SECONDARY, b -> startListening(mapping));
		button.setSize(WIDGET_WIDTH, WIDGET_HEIGHT);
		panel.add(button, 0, y);
		return y + WIDGET_HEIGHT + ArashiTheme.GAP;
	}

	private void startListening(KeyMapping mapping) {
		listeningFor = mapping;
	}

	private String keyLabel(String action, KeyMapping mapping) {
		if (mapping == listeningFor) {
			return action + ": > Press a key, Esc to cancel <";
		}

		return action + ": " + mapping.getTranslatedKeyMessage().getString();
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
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		config.save();
		super.onClose();
	}

	private static double widthToSlider(float width) {
		return (width - MIN_OUTLINE_WIDTH) / (MAX_OUTLINE_WIDTH - MIN_OUTLINE_WIDTH);
	}

	private static double sliderToWidth(double value) {
		return MIN_OUTLINE_WIDTH + value * (MAX_OUTLINE_WIDTH - MIN_OUTLINE_WIDTH);
	}

	private static int withChannel(int rgb, int shift, int value) {
		return (rgb & ~(0xFF << shift)) | ((value & 0xFF) << shift);
	}
}
