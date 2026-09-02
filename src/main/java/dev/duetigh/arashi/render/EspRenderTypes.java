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
 * depth buffer would otherwise reject). The textured-quads pipeline lives separately in
 * {@link TexturedEspPipeline} - see its javadoc for why. ESP's own outline mode uses the vanilla
 * Gizmos API instead of LINES below (see {@link EspRenderer}); LINES stays here for
 * {@link TrackingRenderer}/{@link WaypointRenderer}, which went back to it after their Gizmos-based
 * lines rendered duplicated (drained less often than they were resubmitted - see conversation).
 *
 * <p>FILLED_BOX (translucent fill/overlay mode) used to visibly lag behind the camera during fast
 * movement - not because of {@code sortOnUpload()} (dropping it just overshot the other way,
 * leading instead of lagging, since it only reorders quads within an already-built buffer for
 * correct blending and never touches vertex positions), but because {@link EspRenderer} was
 * submitting its geometry from the wrong point in the frame; see its javadoc and
 * {@link EspRenderer#register()}. Left {@code sortOnUpload()} on regardless since translucent
 * fill still needs correct back-to-front blending between overlapping boxes.
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
