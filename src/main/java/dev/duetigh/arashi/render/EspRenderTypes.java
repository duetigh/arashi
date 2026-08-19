package dev.duetigh.arashi.render;

import java.util.Optional;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
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

	// Based on DEBUG_FILLED_SNIPPET (not GUI_TEXTURED_SNIPPET) because its shader is confirmed to use
	// the same ModelViewMat/ProjMat world-space uniforms as position_color.vsh, which FILLED_BOX above
	// already renders correctly with; GUI snippets are for 2D screen-space widgets instead.
	private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withVertexShader("core/position_tex_color")
					.withFragmentShader("core/position_tex_color")
					.withSampler("Sampler0")
					.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
					.withDepthStencilState(Optional.empty())
					.withLocation(Identifier.fromNamespaceAndPath("arashi", "esp_textured_quads"))
					.build());

	static final RenderType TEXTURED_BOX = RenderType.create("arashi_esp_textured_quads",
			RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
					.withTexture("Sampler0", Sheets.BLOCKS_MAPPER.sheet())
					.sortOnUpload()
					.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
					.createRenderSetup());

	private EspRenderTypes() {
	}
}
