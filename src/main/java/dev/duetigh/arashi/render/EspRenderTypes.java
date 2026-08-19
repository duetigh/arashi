package dev.duetigh.arashi.render;

import java.util.Optional;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Depth-test-disabled variants of the vanilla filled-box/lines pipelines, so ESP geometry draws
 * through terrain instead of being occluded by it (an x-ray box behind a wall is still a box the
 * depth buffer would otherwise reject).
 */
final class EspRenderTypes {
	private static final RenderPipeline QUADS_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withDepthStencilState(Optional.empty())
					.withLocation(Identifier.fromNamespaceAndPath("arashi", "esp_quads"))
					.build());

	private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
					.withDepthStencilState(Optional.empty())
					.withLocation(Identifier.fromNamespaceAndPath("arashi", "esp_lines"))
					.build());

	static final RenderType FILLED_BOX = RenderType.create("arashi_esp_quads",
			RenderSetup.builder(QUADS_PIPELINE)
					.sortOnUpload()
					.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
					.createRenderSetup());

	static final RenderType LINES = RenderType.create("arashi_esp_lines",
			RenderSetup.builder(LINES_PIPELINE)
					.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
					.createRenderSetup());

	private EspRenderTypes() {
	}
}
