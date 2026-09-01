package dev.duetigh.arashi.automation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.scanner.BlockScanner;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * While enabled, right-clicks a chest once it's actually targeted - {@code Minecraft.hitResult} is
 * the exact same field the F3 debug screen's "Targeted Block" line reads, vanilla's own
 * interact-reach pick. This never touches the attack/use key state, so it can't interrupt a held
 * left-click mine: it calls the same interaction the game itself would run for a manual right
 * click, independent of whatever {@code options.keyAttack} is doing that tick.
 */
public final class AutoChestHandler {
	private static final int COOLDOWN_TICKS = 5;

	private final ArashiConfig config;
	private final BlockScanner scanner;
	private BlockPos lastClickedPos;
	private int cooldown;

	public AutoChestHandler(ArashiConfig config, BlockScanner scanner) {
		this.config = config;
		this.scanner = scanner;
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft client) {
		if (cooldown > 0) {
			cooldown--;
		}

		// Reuses the same Crystal-Hollows-only suppression BlockScanner already tracks for ESP
		// scanning, so auto chest turns off outside Crystal Hollows the same way scanning does.
		if (!config.autoChestEnabled() || scanner.isScanningSuppressed() || client.player == null || client.level == null) {
			return;
		}

		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos pos = hit.getBlockPos();
		BlockState state = client.level.getBlockState(pos);

		if (!(state.getBlock() instanceof AbstractChestBlock<?>)) {
			return;
		}

		// Cooldown is per-position, not global - looking at a different chest right after clicking
		// one isn't blocked, but re-clicking that exact chest is, for COOLDOWN_TICKS.
		if (pos.equals(lastClickedPos) && cooldown > 0) {
			return;
		}

		LocalPlayer player = client.player;
		client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
		player.swing(InteractionHand.MAIN_HAND);
		lastClickedPos = pos;
		cooldown = COOLDOWN_TICKS;
	}
}
