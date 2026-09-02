package dev.duetigh.arashi.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.duetigh.arashi.ArashiClient;
import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.config.EspMode;
import dev.duetigh.arashi.config.ScanMode;
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
	// A block this common (e.g. coal ore) can produce far more matches within render distance than
	// a single frame's vertex buffer should take - cap how many boxes get drawn, prioritizing the
	// nearest ones, rather than risk overrunning it (crashed with "Not building!" from BufferBuilder
	// once matches climbed into the tens of thousands).
	private static final int MAX_RENDERED_BOXES = 4000;

	private final BlockScanner scanner;
	private final ArashiConfig config;
	private final Map<Block, TextureAtlasSprite> spriteCache = new HashMap<>();
	private final Map<Block, String> blockIdCache = new HashMap<>();
	// TexturedEspPipeline hand-builds a RenderPipeline through an API that some Minecraft versions
	// don't ship (see its javadoc) - once that's confirmed missing on this game version, stop
	// retrying every frame and stay silent instead of logging the same warning repeatedly.
	private boolean texturedEspUnavailable = false;

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

		if (config.scanMode() == ScanMode.TRACKING && !config.trackingShowBoxEsp()) {
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
		double renderDistanceSq = (double) renderDistanceBlocks * renderDistanceBlocks;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Frustum frustum = context.levelState().cameraRenderState.cullFrustum;
		float fillOpacity = config.fillOpacity();
		float outlineOpacity = config.outlineOpacity();
		int textureTint = EspGeometry.withOpacity(0xFFFFFF, fillOpacity);
		float outlineWidth = config.outlineWidth();

		List<Map.Entry<BlockPos, Block>> candidates = new ArrayList<>(matches.size());

		for (Map.Entry<BlockPos, Block> entry : matches.entrySet()) {
			if (entry.getKey().distToCenterSqr(camera.x, camera.y, camera.z) <= renderDistanceSq) {
				candidates.add(entry);
			}
		}

		if (candidates.size() > MAX_RENDERED_BOXES) {
			candidates.sort(Comparator.comparingDouble(e -> e.getKey().distToCenterSqr(camera.x, camera.y, camera.z)));
			candidates = candidates.subList(0, MAX_RENDERED_BOXES);
		}

		SubmitNodeCollector collector = context.submitNodeCollector();
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);

		// visible is computed once (with each box's face-exposure against same-type neighbors) and
		// reused across the passes below, so frustum culling and the 6 adjacency lookups only run
		// once per candidate no matter how many of fill/texture/outline are active.
		List<VisibleBox> visible = new ArrayList<>(candidates.size());

		for (Map.Entry<BlockPos, Block> entry : candidates) {
			BlockPos pos = entry.getKey();

			if (frustum.isVisible(new AABB(pos))) {
				visible.add(new VisibleBox(pos, entry.getValue(), exposedFaces(pos, entry.getValue(), matches)));
			}
		}

		// Each RenderType's geometry is submitted as its own callback rather than interleaving writes
		// to several buffers at once - holding several different buffers open at once and interleaving
		// writes across them is exactly what crashed with "Not building!" from BufferBuilder once other
		// world-space renderers (tracking mode's line, waypoint markers) started sharing the same
		// bufferSource within the same frame, and the submit-node model keeps that same one-buffer-at-a-
		// time discipline.
		if (drawColorFill) {
			collector.submitCustomGeometry(poseStack, EspRenderTypes.FILLED_BOX, (PoseStack.Pose fillPose, VertexConsumer fillBuffer) -> {
				for (VisibleBox box : visible) {
					String blockId = idFor(box.block);
					EspGeometry.drawFilledBox(fillPose, fillBuffer, new AABB(box.pos),
							EspGeometry.withOpacity(config.fillColorFor(blockId), fillOpacity), box.exposed);
				}
			});
		}

		if (drawTextureFill && !texturedEspUnavailable) {
			try {
				collector.submitCustomGeometry(poseStack, TexturedEspPipeline.TEXTURED_BOX, (PoseStack.Pose texturePose, VertexConsumer textureBuffer) -> {
					for (VisibleBox box : visible) {
						EspGeometry.drawTexturedBox(texturePose, textureBuffer, new AABB(box.pos), spriteFor(box.block), textureTint, box.exposed);
					}
				});
			} catch (LinkageError e) {
				// TexturedEspPipeline references a RenderPipeline.Builder API this Minecraft version
				// doesn't ship (see its javadoc) - fall back to no texture fill rather than crash the
				// whole renderer the way an unguarded reference to it would.
				texturedEspUnavailable = true;
				ArashiClient.LOGGER.warn("Arashi: textured ESP fill isn't supported on this Minecraft version, disabling it", e);
			}
		}

		if (drawOutline) {
			collector.submitCustomGeometry(poseStack, EspRenderTypes.LINES, (PoseStack.Pose outlinePose, VertexConsumer outlineBuffer) -> {
				for (VisibleBox box : visible) {
					String blockId = idFor(box.block);
					EspGeometry.drawLineBox(outlinePose, outlineBuffer, new AABB(box.pos),
							EspGeometry.withOpacity(config.outlineColorFor(blockId), outlineOpacity), outlineWidth, box.exposed);
				}
			});
		}

		poseStack.popPose();
	}

	/** A matched block's own faces don't get exposed=false; only its neighbors' faces do. */
	private static boolean[] exposedFaces(BlockPos pos, Block block, Map<BlockPos, Block> matches) {
		return new boolean[] {
				matches.get(pos.west()) != block,
				matches.get(pos.east()) != block,
				matches.get(pos.below()) != block,
				matches.get(pos.above()) != block,
				matches.get(pos.north()) != block,
				matches.get(pos.south()) != block,
		};
	}

	private record VisibleBox(BlockPos pos, Block block, boolean[] exposed) {
	}

	private TextureAtlasSprite spriteFor(Block block) {
		return spriteCache.computeIfAbsent(block, b -> Minecraft.getInstance().getModelManager()
				.getBlockStateModelSet().getParticleMaterial(b.defaultBlockState()).sprite());
	}

	private String idFor(Block block) {
		return blockIdCache.computeIfAbsent(block, b -> BuiltInRegistries.BLOCK.getKey(b).toString());
	}

}
