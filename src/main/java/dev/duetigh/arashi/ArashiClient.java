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
import com.mojang.brigadier.arguments.StringArgumentType;

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
import dev.duetigh.arashi.gui.PartyScreen;
import dev.duetigh.arashi.hypixel.HypixelWidgetReader;
import dev.duetigh.arashi.hypixel.LobbyTracker;
import dev.duetigh.arashi.party.PartyManager;
import dev.duetigh.arashi.render.DebugHudRenderer;
import dev.duetigh.arashi.render.EspRenderer;
import dev.duetigh.arashi.render.LobbySearchedHudRenderer;
import dev.duetigh.arashi.scan.ScanController;
import dev.duetigh.arashi.scan.ScanEntry;
import dev.duetigh.arashi.scan.ScanStore;
import dev.duetigh.arashi.gui.ScanBrowserScreen;
import dev.duetigh.arashi.scanner.BlockScanner;
import dev.duetigh.arashi.scanner.VeinMatch;
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
	private ScanController scanController;
	private ScanStore scanStore;
	private HypixelWidgetReader hypixelReader;
	private LobbyTracker lobbyTracker;
	private PartyManager party;
	private KeyMapping openScannerKey;
	private KeyMapping toggleEspKey;
	private KeyMapping toggleScanKey;
	private KeyMapping openScanBrowserKey;
	private KeyMapping copyLastCoordsKey;
	private volatile BlockPos lastVeinCoords;
	private boolean hasSentJoinMessage;
	private boolean scanningSuppressed;

	@Override
	public void onInitializeClient() {
		config = ArashiConfig.load();
		scanner = new BlockScanner();
		scanner.setTrackedBlockIds(config.trackedBlockIds());
		scanner.setNewMatchListener(this::onNewMatches);
		scanStore = ScanStore.load();
		scanController = new ScanController(scanner, scanStore);
		scanController.setOnStop(this::onScanAutoStopped);
		hypixelReader = new HypixelWidgetReader();
		lobbyTracker = new LobbyTracker();
		party = new PartyManager(lobbyTracker);

		new EspRenderer(scanner, config).register();
		new DebugHudRenderer(scanner).register();
		new LobbySearchedHudRenderer(config, lobbyTracker).register();

		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> scanner.onChunkLoad(level, chunk));
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> scanController.onChunkLoad(level, chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> scanner.onChunkUnload(chunk));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> scanController.autoStop("disconnect"));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			scanner.clear();

			if (!hasSentJoinMessage) {
				client.player.sendSystemMessage(joinMessage());
				hasSentJoinMessage = true;
			}

			UpdateChecker.UpdateInfo pending = pendingUpdate.getAndSet(null);

			if (pending != null) {
				client.player.sendSystemMessage(updateMessage(pending));
			}
		});

		registerKeyBinding();
		registerCommand();
		registerIncrementalRescan();
		registerPartyListeners();

		if (!config.partyServerUrl().isBlank()) {
			party.connect(config.partyServerUrl());
		}

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
		toggleEspKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.arashi.toggle_esp", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		toggleScanKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.arashi.toggle_scan", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		openScanBrowserKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.arashi.open_scan_browser", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		copyLastCoordsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.arashi.copy_last_coords", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openScannerKey.consumeClick()) {
				client.setScreen(new BlockSelectScreen(config, scanner, scanController, scanStore,
						openScannerKey, toggleEspKey, toggleScanKey, openScanBrowserKey, copyLastCoordsKey));
			}

			while (toggleEspKey.consumeClick()) {
				config.setEspEnabled(!config.espEnabled());
				config.save();
			}

			while (toggleScanKey.consumeClick()) {
				toggleScan();
			}

			while (openScanBrowserKey.consumeClick()) {
				client.setScreen(new ScanBrowserScreen(client.screen, scanController, scanStore));
			}

			while (copyLastCoordsKey.consumeClick()) {
				copyLastCoordsToClipboard();
			}
		});
	}

	private void toggleScanIfInactive() {
		if (!scanController.isActive()) {
			toggleScan();
		}
	}

	private void toggleScanIfActive() {
		if (scanController.isActive()) {
			toggleScan();
		}
	}

	private void toggleScan() {
		Minecraft client = Minecraft.getInstance();

		if (scanController.isActive()) {
			ScanEntry entry = scanController.stop();

			if (client.player != null && entry != null) {
				client.player.sendSystemMessage(prefixed("Saved scan \"" + entry.name() + "\" (" + entry.chunkCount() + " chunks)."));
			}
		} else if (client.level != null) {
			scanController.start(client.level);

			if (client.player != null) {
				client.player.sendSystemMessage(prefixed("Scan started."));
			}
		}
	}

	private void copyLastCoordsToClipboard() {
		Minecraft client = Minecraft.getInstance();
		BlockPos pos = lastVeinCoords;

		if (pos == null) {
			if (client.player != null) {
				client.player.sendSystemMessage(prefixed("No coordinates to copy yet."));
			}

			return;
		}

		String coords = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
		client.keyboardHandler.setClipboard(coords);

		if (client.player != null) {
			client.player.sendSystemMessage(prefixed("Copied " + coords + " to clipboard."));
		}
	}

	private void onScanAutoStopped(ScanEntry entry, String reason) {
		Minecraft client = Minecraft.getInstance();

		if (client.player != null) {
			client.player.sendSystemMessage(prefixed("Scan auto-stopped (" + reason + ") and saved as \""
					+ entry.name() + "\" (" + entry.chunkCount() + " chunks)."));
		}
	}

	private void registerIncrementalRescan() {
		// No client-side "block changed" event exists in Fabric API, so nearby edits inside an
		// already-loaded chunk (mining/building) are picked up by throttled polling of just the
		// chunk the player stands in, rather than a full-area rescan every tick.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				return;
			}

			if (client.player.tickCount % 20 == 0) {
				scanner.rescanChunkAt(client.player.blockPosition());
				scanController.checkDimension(client.level);

				hypixelReader.tick(client.level);
				hypixelReader.lobbyId().ifPresent(lobbyTracker::onLobbyObserved);

				boolean shouldSuppress = config.restrictToCrystalHollows()
						&& hypixelReader.isConnectedToHypixel()
						&& !hypixelReader.isInCrystalHollowsStable();

				if (shouldSuppress != scanningSuppressed) {
					scanningSuppressed = shouldSuppress;
					scanner.setScanningSuppressed(shouldSuppress);

					if (!shouldSuppress) {
						scanner.setTrackedBlockIds(config.trackedBlockIds());
					}
				}
			}
		});
	}

	private void registerCommand() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("arashi")
						.executes(context -> {
							Minecraft client = Minecraft.getInstance();
							client.execute(() -> client.setScreen(new BlockSelectScreen(config, scanner, scanController, scanStore,
									openScannerKey, toggleEspKey, toggleScanKey, openScanBrowserKey, copyLastCoordsKey)));
							return 1;
						})
						.then(ClientCommands.literal("update").executes(context -> {
							requestUpdateDownload();
							return 1;
						}))
						.then(ClientCommands.literal("scan")
								.executes(context -> {
									Minecraft client = Minecraft.getInstance();
									client.execute(() -> client.setScreen(new ScanBrowserScreen(client.screen, scanController, scanStore)));
									return 1;
								})
								.then(ClientCommands.literal("start").executes(context -> {
									Minecraft.getInstance().execute(this::toggleScanIfInactive);
									return 1;
								}))
								.then(ClientCommands.literal("stop").executes(context -> {
									Minecraft.getInstance().execute(this::toggleScanIfActive);
									return 1;
								})))
						.then(ClientCommands.literal("party")
								.executes(context -> {
									Minecraft client = Minecraft.getInstance();
									client.execute(() -> client.setScreen(new PartyScreen(party)));
									return 1;
								})
								.then(ClientCommands.literal("accept")
										.then(ClientCommands.argument("inviteId", StringArgumentType.string())
												.executes(context -> {
													party.respondToInvite(StringArgumentType.getString(context, "inviteId"), true);
													return 1;
												})))
								.then(ClientCommands.literal("decline")
										.then(ClientCommands.argument("inviteId", StringArgumentType.string())
												.executes(context -> {
													party.respondToInvite(StringArgumentType.getString(context, "inviteId"), false);
													return 1;
												}))))));
	}

	private void registerPartyListeners() {
		party.setOnInviteReceived(invite -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				client.player.sendSystemMessage(partyInviteMessage(invite));
			}
		});

		party.setOnInviteResult(outcome -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) {
				return;
			}

			String verb = outcome.accepted() ? "accepted"
					: "EXPIRED".equals(outcome.reason()) ? "did not respond in time to"
					: "declined";
			client.player.sendSystemMessage(prefixed(outcome.targetUsername() + " " + verb + " your party invite."));
		});

		party.setOnKicked(() -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				client.player.sendSystemMessage(prefixed("You were removed from the party.").withStyle(ChatFormatting.RED));
			}
		});
	}

	private static MutableComponent partyInviteMessage(PartyManager.IncomingInvite invite) {
		MutableComponent accept = Component.literal("[Accept]").withStyle(style -> style
				.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand("/arashi party accept " + invite.inviteId()))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to accept"))));
		MutableComponent decline = Component.literal("[Decline]").withStyle(style -> style
				.withColor(ChatFormatting.RED)
				.withClickEvent(new ClickEvent.RunCommand("/arashi party decline " + invite.inviteId()))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to decline"))));

		return Component.literal("[").append(GradientText.arashi())
				.append(Component.literal("] " + invite.fromUsername() + " invited you to their party! "))
				.append(accept)
				.append(Component.literal(" "))
				.append(decline);
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

	private void onNewMatches(ClientLevel level, Map<Block, List<VeinMatch>> newVeins) {
		for (List<VeinMatch> veins : newVeins.values()) {
			for (VeinMatch vein : veins) {
				lastVeinCoords = vein.center();
			}
		}

		if (!config.chatCoordsEnabled()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		client.execute(() -> {
			if (client.player == null) {
				return;
			}

			for (Map.Entry<Block, List<VeinMatch>> entry : newVeins.entrySet()) {
				client.player.sendSystemMessage(matchMessage(entry.getKey(), entry.getValue()));
			}
		});
	}

	private static MutableComponent matchMessage(Block block, List<VeinMatch> veins) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		String coords = veins.stream()
				.map(vein -> {
					BlockPos pos = vein.center();
					String coord = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
					return vein.size() > 1 ? coord + " x" + vein.size() : coord;
				})
				.collect(Collectors.joining(", "));

		return prefixed(BlockDisplay.shortName(id) + " found at " + coords);
	}

	private static MutableComponent joinMessage() {
		return Component.literal("[").append(GradientText.arashi())
				.append(Component.literal("] Mod loaded. (" + currentVersion() + ")"));
	}

	private static MutableComponent prefixed(String message) {
		return Component.literal("[").append(GradientText.arashi()).append(Component.literal("] " + message));
	}

	private static String currentVersion() {
		return FabricLoader.getInstance()
				.getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("?");
	}

	private static MutableComponent updateMessage(UpdateChecker.UpdateInfo info) {
		MutableComponent versionText = Component.literal(currentVersion() + " -> " + info.version());

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
