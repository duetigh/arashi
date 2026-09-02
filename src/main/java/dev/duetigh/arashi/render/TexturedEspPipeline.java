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
 * The textured-quads ESP pipeline ({@link dev.duetigh.arashi.config.EspMode#TEXTURE}), kept out of
 * {@link EspRenderTypes} on its own because it's the one piece of ESP rendering that isn't
 * forward-compatible: it hand-builds a {@link RenderPipeline} via {@code withVertexFormat}/
 * {@code withSampler}, an API {@code RenderPipeline.Builder} dropped in favor of
 * {@code withVertexBinding}/{@code withBindGroupLayout} on Minecraft versions newer than this mod
 * compiles against. Referencing that removed API anywhere in {@code EspRenderTypes} itself would
 * take the depth-tested-safe FILLED_BOX/LINES pipelines down with it the moment the JVM verifies
 * that class - isolating it in its own class means only {@code EspMode.TEXTURE} fails (caught by
 * {@link EspRenderer}), not the rest of the ESP.
 */
final class TexturedEspPipeline {
	// Based on DEBUG_FILLED_SNIPPET (not GUI_TEXTURED_SNIPPET) because its shader is confirmed to use
	// the same ModelViewMat/ProjMat world-space uniforms as position_color.vsh, which FILLED_BOX
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

	private TexturedEspPipeline() {
	}
}
