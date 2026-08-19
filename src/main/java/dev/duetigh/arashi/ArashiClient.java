package dev.duetigh.arashi;

import java.util.concurrent.atomic.AtomicReference;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.gui.BlockSelectScreen;
import dev.duetigh.arashi.render.EspRenderer;
import dev.duetigh.arashi.scanner.BlockScanner;
import dev.duetigh.arashi.text.GradientText;
import dev.duetigh.arashi.update.UpdateChecker;

public final class ArashiClient implements ClientModInitializer {
	public static final String MOD_ID = "arashi";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final AtomicReference<String> pendingUpdateVersion = new AtomicReference<>();

	private ArashiConfig config;
	private BlockScanner scanner;
	private KeyMapping openScannerKey;

	@Override
	public void onInitializeClient() {
		config = ArashiConfig.load();
		scanner = new BlockScanner();
		scanner.setTrackedBlockIds(config.trackedBlockIds());

		new EspRenderer(scanner).register();

		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> scanner.onChunkLoad(level, chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> scanner.onChunkUnload(chunk));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			scanner.clear();
			client.player.sendSystemMessage(joinMessage());
			String pending = pendingUpdateVersion.getAndSet(null);

			if (pending != null) {
				client.player.sendSystemMessage(updateMessage(pending));
			}
		});

		registerKeyBinding();
		registerCommand();
		registerIncrementalRescan();

		UpdateChecker.checkAsync(newVersion -> {
			Minecraft client = Minecraft.getInstance();

			if (client.player != null) {
				client.player.sendSystemMessage(updateMessage(newVersion));
			} else {
				pendingUpdateVersion.set(newVersion);
			}
		});
	}

	private void registerKeyBinding() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openScannerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.arashi.open_scanner", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openScannerKey.consumeClick()) {
				client.setScreen(new BlockSelectScreen(config, scanner));
			}
		});
	}

	private void registerIncrementalRescan() {
		// No client-side "block changed" event exists in Fabric API, so nearby edits inside an
		// already-loaded chunk (mining/building) are picked up by throttled polling of just the
		// chunk the player stands in, rather than a full-area rescan every tick.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}

			if (client.player.tickCount % 20 == 0) {
				scanner.rescanChunkAt(client.player.blockPosition());
			}
		});
	}

	private void registerCommand() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("arashi").executes(context -> {
					Minecraft client = Minecraft.getInstance();
					client.execute(() -> client.setScreen(new BlockSelectScreen(config, scanner)));
					return 1;
				})));
	}

	private static MutableComponent joinMessage() {
		return Component.literal("[").append(GradientText.arashi()).append(Component.literal("] Mod loaded."));
	}

	private static MutableComponent updateMessage(String newVersion) {
		String currentVersion = FabricLoader.getInstance()
				.getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("?");

		return Component.literal("[").append(GradientText.arashi())
				.append(Component.literal("] Update available! Version " + currentVersion + " -> " + newVersion));
	}
}
