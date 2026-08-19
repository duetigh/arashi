package dev.duetigh.arashi.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.config.EspMode;
import dev.duetigh.arashi.scanner.BlockScanner;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Draws a translucent box, textured box, and/or wireframe outline over every tracked block
 * position. Uses depth-test-disabled render types (see {@link EspRenderTypes}) so boxes stay
 * visible through terrain instead of being occluded like normal world geometry - that's the
 * point of an ESP.
 */
public final class EspRenderer {
	private final BlockScanner scanner;
	private final ArashiConfig config;
	private final Map<Block, TextureAtlasSprite> spriteCache = new HashMap<>();
	private final Map<Block, String> blockIdCache = new HashMap<>();

	public EspRenderer(BlockScanner scanner, ArashiConfig config) {
		this.scanner = scanner;
		this.config = config;
	}

	public void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::render);
	}

	private void render(LevelRenderContext context) {
		if (!config.espEnabled()) {
			return;
		}

		Map<BlockPos, Block> matches = scanner.matchesWithBlocks();

		if (matches.isEmpty()) {
			return;
		}

		EspMode mode = config.espMode();
		boolean drawColorFill = mode == EspMode.OVERLAY || mode == EspMode.BOTH;
		boolean drawTextureFill = mode == EspMode.TEXTURE;
		boolean drawOutline = mode == EspMode.OUTLINE || mode == EspMode.BOTH;

		Minecraft client = Minecraft.getInstance();
		int renderDistanceBlocks = client.options.renderDistance().get() * 16;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Frustum frustum = context.levelState().cameraRenderState.cullFrustum;
		float fillOpacity = config.fillOpacity();
		float outlineOpacity = config.outlineOpacity();
		int textureTint = withOpacity(0xFFFFFF, fillOpacity);
		float outlineWidth = config.outlineWidth();

		MultiBufferSource.BufferSource bufferSource = context.bufferSource();
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		PoseStack.Pose pose = poseStack.last();

		VertexConsumer fillBuffer = drawColorFill ? bufferSource.getBuffer(EspRenderTypes.FILLED_BOX) : null;
		VertexConsumer textureBuffer = drawTextureFill ? bufferSource.getBuffer(EspRenderTypes.TEXTURED_BOX) : null;
		VertexConsumer outlineBuffer = drawOutline ? bufferSource.getBuffer(EspRenderTypes.LINES) : null;

		for (Map.Entry<BlockPos, Block> entry : matches.entrySet()) {
			BlockPos pos = entry.getKey();

			if (pos.distToCenterSqr(camera.x, camera.y, camera.z) > (double) renderDistanceBlocks * renderDistanceBlocks) {
				continue;
			}

			AABB box = new AABB(pos);

			if (!frustum.isVisible(box)) {
				continue;
			}

			Block block = entry.getValue();
			String blockId = idFor(block);

			if (fillBuffer != null) {
				drawFilledBox(pose, fillBuffer, box, withOpacity(config.fillColorFor(blockId), fillOpacity));
			}

			if (textureBuffer != null) {
				drawTexturedBox(pose, textureBuffer, box, spriteFor(block), textureTint);
			}

			if (outlineBuffer != null) {
				drawLineBox(pose, outlineBuffer, box, withOpacity(config.outlineColorFor(blockId), outlineOpacity), outlineWidth);
			}
		}

		poseStack.popPose();
	}

	private TextureAtlasSprite spriteFor(Block block) {
		return spriteCache.computeIfAbsent(block, b -> Minecraft.getInstance().getModelManager()
				.getBlockStateModelSet().getParticleMaterial(b.defaultBlockState()).sprite());
	}

	private String idFor(Block block) {
		return blockIdCache.computeIfAbsent(block, b -> BuiltInRegistries.BLOCK.getKey(b).toString());
	}

	private static int withOpacity(int rgb, float alpha) {
		float r = ((rgb >> 16) & 0xFF) / 255.0f;
		float g = ((rgb >> 8) & 0xFF) / 255.0f;
		float b = (rgb & 0xFF) / 255.0f;
		return ARGB.colorFromFloat(alpha, r, g, b);
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

	// Every face below cycles its 4 corners in the same "low, up, up-across, low-across" order as
	// drawFilledBox, so the same 4-corner UV cycle (bottom-left, top-left, top-right, bottom-right
	// of the sprite) tiles correctly on all 6 faces without per-face UV bookkeeping.
	private static void drawTexturedBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, TextureAtlasSprite sprite, int color) {
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();

		// Front (-Z)
		texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.minZ, u0, v1, color);
		texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.minZ, u0, v0, color);
		texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.minZ, u1, v0, color);
		texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.minZ, u1, v1, color);
		// Back (+Z)
		texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.maxZ, u0, v1, color);
		texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.maxZ, u0, v0, color);
		texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.maxZ, u1, v0, color);
		texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.maxZ, u1, v1, color);
		// Left (-X)
		texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.maxZ, u0, v1, color);
		texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.maxZ, u0, v0, color);
		texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.minZ, u1, v0, color);
		texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.minZ, u1, v1, color);
		// Right (+X)
		texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.minZ, u0, v1, color);
		texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.minZ, u0, v0, color);
		texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.maxZ, u1, v0, color);
		texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.maxZ, u1, v1, color);
		// Top (+Y)
		texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.minZ, u0, v1, color);
		texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.maxZ, u0, v0, color);
		texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.maxZ, u1, v0, color);
		texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.minZ, u1, v1, color);
		// Bottom (-Y)
		texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.maxZ, u0, v1, color);
		texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.minZ, u0, v0, color);
		texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.minZ, u1, v0, color);
		texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.maxZ, u1, v1, color);
	}

	private static void texVertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color) {
		buffer.addVertex(pose, x, y, z).setUv(u, v).setColor(color);
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
