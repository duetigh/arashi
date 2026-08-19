package dev.duetigh.arashi;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

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
import dev.duetigh.arashi.render.DebugHudRenderer;
import dev.duetigh.arashi.render.EspRenderer;
import dev.duetigh.arashi.scanner.BlockScanner;
import dev.duetigh.arashi.text.GradientText;
import dev.duetigh.arashi.update.UpdateChecker;
import dev.duetigh.arashi.util.BlockDisplay;

public final class ArashiClient implements ClientModInitializer {
	public static final String MOD_ID = "arashi";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final AtomicReference<UpdateChecker.UpdateInfo> pendingUpdate = new AtomicReference<>();
	private final AtomicReference<UpdateChecker.UpdateInfo> latestUpdate = new AtomicReference<>();
	private final AtomicBoolean updateInProgress = new AtomicBoolean();

	private ArashiConfig config;
	private BlockScanner scanner;
	private KeyMapping openScannerKey;

	@Override
	public void onInitializeClient() {
		config = ArashiConfig.load();
		scanner = new BlockScanner();
		scanner.setTrackedBlockIds(config.trackedBlockIds());
		scanner.setNewMatchListener(this::onNewMatches);

		new EspRenderer(scanner, config).register();
		new DebugHudRenderer(scanner).register();

		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> scanner.onChunkLoad(level, chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> scanner.onChunkUnload(chunk));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			scanner.clear();
			client.player.sendSystemMessage(joinMessage());
			UpdateChecker.UpdateInfo pending = pendingUpdate.getAndSet(null);

			if (pending != null) {
				client.player.sendSystemMessage(updateMessage(pending));
			}
		});

		registerKeyBinding();
		registerCommand();
		registerIncrementalRescan();

		UpdateChecker.checkAsync(info -> {
			latestUpdate.set(info);
			Minecraft client = Minecraft.getInstance();

			if (client.player != null) {
				client.player.sendSystemMessage(updateMessage(info));
			} else {
				pendingUpdate.set(info);
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
				ClientCommands.literal("arashi")
						.executes(context -> {
							Minecraft client = Minecraft.getInstance();
							client.execute(() -> client.setScreen(new BlockSelectScreen(config, scanner)));
							return 1;
						})
						.then(ClientCommands.literal("update").executes(context -> {
							requestUpdateDownload();
							return 1;
						}))));
	}

	private void requestUpdateDownload() {
		UpdateChecker.UpdateInfo info = latestUpdate.get();
		Minecraft client = Minecraft.getInstance();

		if (info == null || info.downloadUrl() == null) {
			if (client.player != null) {
				client.player.sendSystemMessage(prefixed("No update download available."));
			}

			return;
		}

		if (!updateInProgress.compareAndSet(false, true)) {
			return;
		}

		if (client.player != null) {
			client.player.sendSystemMessage(prefixed("Downloading update " + info.version() + "..."));
		}

		UpdateChecker.downloadAndStageAsync(info.downloadUrl(),
				() -> client.execute(() -> {
					if (client.player != null) {
						client.player.sendSystemMessage(prefixed("Update downloaded! It will be installed the next time you close the game."));
					}
				}),
				error -> client.execute(() -> {
					updateInProgress.set(false);

					if (client.player != null) {
						client.player.sendSystemMessage(prefixed("Update download failed: " + error.getMessage())
								.withStyle(ChatFormatting.RED));
					}
				}));
	}

	private void onNewMatches(ClientLevel level, Map<Block, List<BlockPos>> newlyFound) {
		if (!config.chatCoordsEnabled()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		client.execute(() -> {
			if (client.player == null) {
				return;
			}

			for (Map.Entry<Block, List<BlockPos>> entry : newlyFound.entrySet()) {
				client.player.sendSystemMessage(matchMessage(entry.getKey(), entry.getValue()));
			}
		});
	}

	private static MutableComponent matchMessage(Block block, List<BlockPos> positions) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		String coords = positions.stream()
				.map(pos -> "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")")
				.collect(Collectors.joining(", "));

		return prefixed(BlockDisplay.shortName(id) + " found at " + coords);
	}

	private static MutableComponent joinMessage() {
		return Component.literal("[").append(GradientText.arashi()).append(Component.literal("] Mod loaded."));
	}

	private static MutableComponent prefixed(String message) {
		return Component.literal("[").append(GradientText.arashi()).append(Component.literal("] " + message));
	}

	private static MutableComponent updateMessage(UpdateChecker.UpdateInfo info) {
		String currentVersion = FabricLoader.getInstance()
				.getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("?");

		MutableComponent versionText = Component.literal(currentVersion + " -> " + info.version());

		if (info.downloadUrl() != null) {
			versionText = versionText.withStyle(style -> style
					.withColor(ChatFormatting.AQUA)
					.withUnderlined(true)
					.withClickEvent(new ClickEvent.RunCommand("/arashi update"))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to download and install on next restart"))));
		}

		return Component.literal("[").append(GradientText.arashi())
				.append(Component.literal("] Update available! Version "))
				.append(versionText);
	}
}
