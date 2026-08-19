package dev.duetigh.arashi.render;

import java.awt.Color;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.config.EspMode;
import dev.duetigh.arashi.scanner.BlockScanner;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Draws a translucent box and/or wireframe outline over every tracked block position, through
 * terrain, using the submit-node geometry path so the boxes are batched with the rest of the frame.
 */
public final class EspRenderer {
	private static final float RED = 1.0f;
	private static final float GREEN = 0.2f;
	private static final float BLUE = 0.2f;

	private final BlockScanner scanner;
	private final ArashiConfig config;

	public EspRenderer(BlockScanner scanner, ArashiConfig config) {
		this.scanner = scanner;
		this.config = config;
	}

	public void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::render);
	}

	private void render(LevelRenderContext context) {
		List<BlockPos> matches = scanner.matches();

		if (matches.isEmpty()) {
			return;
		}

		EspMode mode = config.espMode();
		boolean drawFill = mode != EspMode.OUTLINE;
		boolean drawOutline = mode != EspMode.OVERLAY;

		Minecraft client = Minecraft.getInstance();
		int renderDistanceBlocks = client.options.renderDistance().get() * 16;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		int fillColor = ARGB.colorFromFloat(config.fillOpacity(), RED, GREEN, BLUE);
		int outlineColor = outlineColor();
		float outlineWidth = config.outlineWidth();

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);

		for (BlockPos pos : matches) {
			if (pos.distToCenterSqr(camera.x, camera.y, camera.z) > (double) renderDistanceBlocks * renderDistanceBlocks) {
				continue;
			}

			AABB box = new AABB(pos);

			if (drawFill) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> drawFilledBox(pose, buffer, box, fillColor));
			}

			if (drawOutline) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> drawLineBox(pose, buffer, box, outlineColor, outlineWidth));
			}
		}

		poseStack.popPose();
	}

	private int outlineColor() {
		int rgb = Color.HSBtoRGB(config.outlineHue(), 1.0f, 1.0f);
		float r = ((rgb >> 16) & 0xFF) / 255.0f;
		float g = ((rgb >> 8) & 0xFF) / 255.0f;
		float b = (rgb & 0xFF) / 255.0f;
		return ARGB.colorFromFloat(config.outlineOpacity(), r, g, b);
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

	private static void drawLineBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width) {
		float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
		float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;

		// Bottom face
		edge(pose, buffer, minX, minY, minZ, maxX, minY, minZ, color, width);
		edge(pose, buffer, maxX, minY, minZ, maxX, minY, maxZ, color, width);
		edge(pose, buffer, maxX, minY, maxZ, minX, minY, maxZ, color, width);
		edge(pose, buffer, minX, minY, maxZ, minX, minY, minZ, color, width);
		// Top face
		edge(pose, buffer, minX, maxY, minZ, maxX, maxY, minZ, color, width);
		edge(pose, buffer, maxX, maxY, minZ, maxX, maxY, maxZ, color, width);
		edge(pose, buffer, maxX, maxY, maxZ, minX, maxY, maxZ, color, width);
		edge(pose, buffer, minX, maxY, maxZ, minX, maxY, minZ, color, width);
		// Vertical edges
		edge(pose, buffer, minX, minY, minZ, minX, maxY, minZ, color, width);
		edge(pose, buffer, maxX, minY, minZ, maxX, maxY, minZ, color, width);
		edge(pose, buffer, maxX, minY, maxZ, maxX, maxY, maxZ, color, width);
		edge(pose, buffer, minX, minY, maxZ, minX, maxY, maxZ, color, width);
	}

	private static void edge(PoseStack.Pose pose, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, int color, float width) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float nx = dx / length;
		float ny = dy / length;
		float nz = dz / length;

		buffer.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
		buffer.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
	}
}
