package dev.duetigh.arashi.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.duetigh.arashi.scanner.BlockScanner;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Draws a translucent box over every tracked block position, through terrain,
 * using the submit-node geometry path so the boxes are batched with the rest of the frame.
 */
public final class EspRenderer {
	private static final float ALPHA = 0.45f;
	private static final float RED = 1.0f;
	private static final float GREEN = 0.2f;
	private static final float BLUE = 0.2f;

	private final BlockScanner scanner;

	public EspRenderer(BlockScanner scanner) {
		this.scanner = scanner;
	}

	public void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::render);
	}

	private void render(LevelRenderContext context) {
		List<BlockPos> matches = scanner.matches();

		if (matches.isEmpty()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		int renderDistanceBlocks = client.options.renderDistance().get() * 16;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		int color = ARGB.colorFromFloat(ALPHA, RED, GREEN, BLUE);

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);

		for (BlockPos pos : matches) {
			if (pos.distToCenterSqr(camera.x, camera.y, camera.z) > (double) renderDistanceBlocks * renderDistanceBlocks) {
				continue;
			}

			AABB box = new AABB(pos);
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> drawFilledBox(pose, buffer, box, color));
		}

		poseStack.popPose();
	}

	// Debug-filled-box's pipeline back-face culls, so every quad below is wound counter-clockwise
	// as seen from outside the box (each face's normal points outward).
	private static void drawFilledBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
		// Front (-Z)
		buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ).setColor(color);
		buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.minZ).setColor(color);
		buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.minZ).setColor(color);
		buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ).setColor(color);
		// Back (+Z)
		buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ).setColor(color);
		buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.maxZ).setColor(color);
		buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.maxZ).setColor(color);
		buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.maxZ).setColor(color);
		// Left (-X)
		buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.maxZ).setColor(color);
		buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.maxZ).setColor(color);
		buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.minZ).setColor(color);
		buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ).setColor(color);
		// Right (+X)
		buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ).setColor(color);
		buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.minZ).setColor(color);
		buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.maxZ).setColor(color);
		buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ).setColor(color);
		// Top (+Y)
		buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.minZ).setColor(color);
		buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.maxZ).setColor(color);
		buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.maxZ).setColor(color);
		buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.minZ).setColor(color);
		// Bottom (-Y)
		buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.maxZ).setColor(color);
		buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ).setColor(color);
		buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ).setColor(color);
		buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ).setColor(color);
	}
}
