package dev.duetigh.arashi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.config.ScanMode;
import dev.duetigh.arashi.scanner.TrackingController;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * In tracking mode, draws a single line from the camera (i.e. the crosshair) to the center of
 * the nearest vein of the tracked block, using the same depth-test-disabled line pipeline as
 * {@link EspRenderer} so it reads through terrain like the rest of the ESP.
 */
public final class TrackingRenderer {
	private static final float LINE_WIDTH = 2.0f;
	// A line starting exactly at the camera's eye position (0 distance) sits right on the near clip
	// plane and gets discarded entirely - nudge the start point this far in front of the camera
	// first, along the camera's own forward vector rather than the direction *toward the target*
	// (as this used to do). Nudging toward the target put the start point behind the near plane
	// whenever the target wasn't roughly in front of the player - which is most of the time, since
	// the nearest vein rarely lines up with the crosshair - and the whole line would vanish rather
	// than just clip; "shows only while the tracked block is at your feet and you're looking down at
	// it" was that bug. The offset is kept fairly large (not the bare minimum to clear the near
	// plane) because the whole scene - including this line - shares one bob-inclusive view
	// transform: a point this close to the eye visibly swims with any camera-translation bob simply
	// from parallax (near things swing more than far ones for the same camera sway), same as it
	// would for a held item. Pushing it out further shrinks that swing to where it reads as stable.
	private static final float NEAR_OFFSET = 1.5f;

	private final TrackingController tracking;
	private final ArashiConfig config;

	public TrackingRenderer(TrackingController tracking, ArashiConfig config) {
		this.tracking = tracking;
		this.config = config;
	}

	public void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::render);
	}

	private void render(LevelRenderContext context) {
		if (!config.espEnabled() || config.scanMode() != ScanMode.TRACKING) {
			return;
		}

		BlockPos target = tracking.targetCenter();

		if (target == null) {
			return;
		}

		Vec3 camera = context.levelState().cameraRenderState.pos;
		Vec3 targetCenter = Vec3.atCenterOf(target);

		if (camera.distanceTo(targetCenter) < NEAR_OFFSET) {
			return;
		}

		Vec3 origin = EspGeometry.nearCameraOrigin(camera, context.levelState().cameraRenderState.orientation, NEAR_OFFSET);

		SubmitNodeCollector collector = context.submitNodeCollector();
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);

		collector.submitCustomGeometry(poseStack, EspRenderTypes.LINES, (PoseStack.Pose pose, VertexConsumer buffer) -> {
			EspGeometry.edge(pose, buffer, (float) origin.x, (float) origin.y, (float) origin.z,
					(float) targetCenter.x, (float) targetCenter.y, (float) targetCenter.z, lineColor(), LINE_WIDTH);
		});

		poseStack.popPose();
	}

	/** Matches the tracked block's own ESP outline color, so the line reads as "part of" that block's ESP. */
	private int lineColor() {
		return config.trackedBlockIds().stream()
				.findFirst()
				.map(id -> EspGeometry.withOpacity(config.outlineColorFor(id), config.outlineOpacity()))
				.orElse(0xFFFFFFFF);
	}
}
