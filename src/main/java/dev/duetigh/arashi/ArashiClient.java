package dev.duetigh.arashi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import dev.duetigh.arashi.automation.AutoChestHandler;
import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.config.ScanMode;
import dev.duetigh.arashi.gui.BlockSelectScreen;
import dev.duetigh.arashi.gui.PartyScreen;
import dev.duetigh.arashi.gui.WaypointManagerScreen;
import dev.duetigh.arashi.hypixel.HypixelWidgetReader;
import dev.duetigh.arashi.hypixel.LobbyTracker;
import dev.duetigh.arashi.party.PartyManager;
import dev.duetigh.arashi.render.DebugHudRenderer;
import dev.duetigh.arashi.render.EspRenderer;
import dev.duetigh.arashi.render.LobbySearchedHudRenderer;
import dev.duetigh.arashi.render.TrackingRenderer;
import dev.duetigh.arashi.render.WaypointRenderer;
import dev.duetigh.arashi.scan.ScanController;
import dev.duetigh.arashi.scan.ScanEntry;
import dev.duetigh.arashi.scan.ScanStore;
import dev.duetigh.arashi.gui.ScanBrowserScreen;
import dev.duetigh.arashi.scanner.BlockScanner;
import dev.duetigh.arashi.scanner.TrackingController;
import dev.duetigh.arashi.scanner.VeinMatch;
import dev.duetigh.arashi.text.GradientText;
import dev.duetigh.arashi.update.UpdateChecker;
import dev.duetigh.arashi.util.BlockDisplay;
import dev.duetigh.arashi.waypoint.Waypoint;
import dev.duetigh.arashi.waypoint.WaypointEditorState;
import dev.duetigh.arashi.waypoint.WaypointGroup;
import dev.duetigh.arashi.waypoint.WaypointNavigator;
import dev.duetigh.arashi.waypoint.WaypointStore;
import dev.duetigh.arashi.waypoint.WaypointType;

public final class ArashiClient implements ClientModInitializer {
	public static final String MOD_ID = "arashi";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final AtomicReference<UpdateChecker.UpdateInfo> pendingUpdate = new AtomicReference<>();
	private final AtomicReference<UpdateChecker.UpdateInfo> latestUpdate = new AtomicReference<>();
	private final AtomicBoolean updateInProgress = new AtomicBoolean();

	private ArashiConfig config;
	private BlockScanner scanner;
	private TrackingController trackingController;
	private ScanController scanController;
	private ScanStore scanStore;
	private HypixelWidgetReader hypixelReader;
	private LobbyTracker lobbyTracker;
	private PartyManager party;
	private WaypointStore waypointStore;
	private WaypointEditorState waypointEditorState;
	private WaypointNavigator waypointNavigator;
	private KeyMapping openScannerKey;
	private KeyMapping toggleEspKey;
	private KeyMapping toggleScanKey;
	private KeyMapping openScanBrowserKey;
	private KeyMapping copyLastCoordsKey;
	private KeyMapping openWaypointManagerKey;
	private volatile BlockPos lastVeinCoords;
	private boolean hasSentJoinMessage;
	private boolean scanningSuppressed;

	@Override
	public void onInitializeClient() {
		config = ArashiConfig.load();
		scanner = new BlockScanner();
		scanner.setTrackedBlockIds(config.trackedBlockIds());
		scanner.setNewMatchListener(this::onNewMatches);
		trackingController = new TrackingController(scanner);
		scanStore = ScanStore.load();
		scanController = new ScanController(scanner, scanStore);
		scanController.setOnStop(this::onScanAutoStopped);
		hypixelReader = new HypixelWidgetReader();
		lobbyTracker = new LobbyTracker();
		party = new PartyManager(lobbyTracker);
		waypointStore = WaypointStore.load();
		waypointEditorState = new WaypointEditorState();
		waypointNavigator = new WaypointNavigator(config, waypointStore);

		new EspRenderer(scanner, config).register();
		new TrackingRenderer(trackingController, config).register();
		new DebugHudRenderer(scanner).register();
		new LobbySearchedHudRenderer(config, lobbyTracker).register();
		new AutoChestHandler(config, scanner).register();
		new WaypointRenderer(config, waypointStore, waypointEditorState, waypointNavigator).register();

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
		registerTracking();
		registerWaypointNavigation();
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
		openWaypointManagerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.arashi.open_waypoint_manager", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openScannerKey.consumeClick()) {
				client.setScreen(new BlockSelectScreen(config, scanner, scanController, scanStore,
						waypointStore, waypointEditorState,
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

			while (openWaypointManagerKey.consumeClick()) {
				client.setScreen(new WaypointManagerScreen(client.screen, config, waypointStore, waypointEditorState));
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

			scanner.processPendingRescans();

			if (client.player.tickCount % 20 == 0) {
				scanner.rescanChunkAt(client.player.blockPosition());
				scanner.detectNewVeinsNow();
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

				if (config.scanMode() == ScanMode.TRACKING) {
					trackingController.recompute(client.player.position());
				}
			}
		});
	}

	/**
	 * Tracking mode's per-tick upkeep: keeps {@link TrackingController}'s single tracked block in
	 * sync with config (in case it changed via the GUI) and retargets immediately if the current
	 * vein starts getting mined - the fuller "retarget to whatever's nearest as the player moves"
	 * recompute happens on the slower throttled cadence in {@link #registerIncrementalRescan()}.
	 */
	private void registerTracking() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || config.scanMode() != ScanMode.TRACKING) {
				return;
			}

			Block trackedBlock = config.trackedBlockIds().stream()
					.findFirst()
					.flatMap(id -> BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)))
					.orElse(null);

			trackingController.setTrackedBlock(trackedBlock);
			trackingController.checkTargetIntact(client.player.position());
		});
	}

	/** Advances the active waypoint group's current index as the player reaches each waypoint. */
	private void registerWaypointNavigation() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}

			waypointNavigator.tick(client.player.blockPosition());
		});
	}

	private void registerCommand() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("arashi")
						.executes(context -> {
							Minecraft client = Minecraft.getInstance();
							client.execute(() -> client.setScreen(new BlockSelectScreen(config, scanner, scanController, scanStore,
									waypointStore, waypointEditorState,
									openScannerKey, toggleEspKey, toggleScanKey, openScanBrowserKey, copyLastCoordsKey)));
							return 1;
						})
						.then(ClientCommands.literal("help").executes(context -> {
							sendHelp();
							return 1;
						}))
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
												}))))
						.then(ClientCommands.literal("waypoint")
								.executes(context -> {
									Minecraft client = Minecraft.getInstance();
									client.execute(() -> client.setScreen(
											new WaypointManagerScreen(client.screen, config, waypointStore, waypointEditorState)));
									return 1;
								})
								.then(ClientCommands.literal("new")
										.then(ClientCommands.argument("name", StringArgumentType.greedyString())
												.executes(context -> {
													createAndEditWaypointGroup(StringArgumentType.getString(context, "name"));
													return 1;
												})))
								.then(ClientCommands.literal("edit")
										.then(ClientCommands.argument("name", StringArgumentType.greedyString())
												.executes(context -> {
													editWaypointGroup(StringArgumentType.getString(context, "name"));
													return 1;
												})))
								.then(ClientCommands.literal("add")
										.then(ClientCommands.literal("pickobulus").executes(context -> {
											addWaypoint(WaypointType.PICKOBULUS);
											return 1;
										}))
										.then(ClientCommands.literal("etherwarp").executes(context -> {
											addWaypoint(WaypointType.ETHERWARP);
											return 1;
										})))
								.then(ClientCommands.literal("stop").executes(context -> {
									stopWaypointEditor();
									return 1;
								}))
								.then(ClientCommands.literal("use")
										.then(ClientCommands.argument("name", StringArgumentType.greedyString())
												.executes(context -> {
													useWaypointGroup(StringArgumentType.getString(context, "name"));
													return 1;
												})))
								.then(ClientCommands.literal("clear").executes(context -> {
									clearActiveWaypointGroup();
									return 1;
								})))));
	}

	private void sendHelp() {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			return;
		}

		client.player.sendSystemMessage(prefixed("Commands:"));

		String[] lines = {
				"/arashi - open the block scanner",
				"/arashi help - show this list",
				"/arashi update - download and install the latest update",
				"/arashi scan - open saved scans",
				"/arashi scan start - start a new scan",
				"/arashi scan stop - stop the active scan",
				"/arashi party - open the party menu",
				"/arashi party accept <id> - accept a party invite",
				"/arashi party decline <id> - decline a party invite",
				"/arashi waypoint - open the waypoint manager",
				"/arashi waypoint new <name> - create a waypoint group and start editing it",
				"/arashi waypoint edit <name> - start editing an existing waypoint group",
				"/arashi waypoint add <pickobulus|etherwarp> - add a waypoint at your targeted block",
				"/arashi waypoint stop - stop editing waypoints",
				"/arashi waypoint use <name> - set a group as the active navigation route",
				"/arashi waypoint clear - clear the active navigation route",
		};

		for (String line : lines) {
			client.player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}
	}

	private void addWaypoint(WaypointType type) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			return;
		}

		String groupId = waypointEditorState.editingGroupId();

		if (groupId == null) {
			client.player.sendSystemMessage(prefixed(
					"Not editing a waypoint group. Use /arashi waypoint new <name> or /arashi waypoint edit <name> first.")
					.withStyle(ChatFormatting.RED));
			return;
		}

		BlockPos pos = client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
				? hit.getBlockPos()
				: client.player.blockPosition();

		Waypoint waypoint = waypointStore.addWaypoint(groupId, pos, type);
		client.player.sendSystemMessage(prefixed("Added waypoint #" + waypoint.order() + " (" + type.label() + ") at "
				+ pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "."));
	}

	private void createAndEditWaypointGroup(String name) {
		Minecraft client = Minecraft.getInstance();
		WaypointGroup group = waypointStore.createGroup(name);
		waypointEditorState.enter(group.id());

		if (client.player != null) {
			client.player.sendSystemMessage(prefixed("Created and now editing waypoint group \"" + name + "\"."));
		}
	}

	private void editWaypointGroup(String name) {
		Minecraft client = Minecraft.getInstance();
		Optional<String> id = findGroupIdByName(name);

		if (id.isEmpty()) {
			if (client.player != null) {
				client.player.sendSystemMessage(prefixed("No waypoint group named \"" + name + "\".").withStyle(ChatFormatting.RED));
			}

			return;
		}

		waypointEditorState.enter(id.get());

		if (client.player != null) {
			client.player.sendSystemMessage(prefixed("Now editing waypoint group \"" + name + "\"."));
		}
	}

	private void stopWaypointEditor() {
		waypointEditorState.exit();
		Minecraft client = Minecraft.getInstance();

		if (client.player != null) {
			client.player.sendSystemMessage(prefixed("Stopped editing waypoints."));
		}
	}

	private void useWaypointGroup(String name) {
		Minecraft client = Minecraft.getInstance();
		Optional<String> id = findGroupIdByName(name);

		if (id.isEmpty()) {
			if (client.player != null) {
				client.player.sendSystemMessage(prefixed("No waypoint group named \"" + name + "\".").withStyle(ChatFormatting.RED));
			}

			return;
		}

		config.setActiveWaypointGroupId(id.get());
		config.save();

		if (client.player != null) {
			client.player.sendSystemMessage(prefixed("Now navigating waypoint group \"" + name + "\"."));
		}
	}

	private void clearActiveWaypointGroup() {
		config.setActiveWaypointGroupId(null);
		config.save();
		Minecraft client = Minecraft.getInstance();

		if (client.player != null) {
			client.player.sendSystemMessage(prefixed("Cleared active waypoint route."));
		}
	}

	private Optional<String> findGroupIdByName(String name) {
		return waypointStore.list().stream()
				.filter(entry -> entry.name().equalsIgnoreCase(name))
				.map(WaypointStore.GroupEntry::id)
				.findFirst();
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
