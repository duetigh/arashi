package dev.duetigh.arashi.render;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.waypoint.Waypoint;
import dev.duetigh.arashi.waypoint.WaypointEditorState;
import dev.duetigh.arashi.waypoint.WaypointGroup;
import dev.duetigh.arashi.waypoint.WaypointNavigator;
import dev.duetigh.arashi.waypoint.WaypointStore;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Draws a colored marker box and an optional camera-facing floating label ("#order type") over
 * waypoints - every waypoint in the group being edited while the waypoint editor is active,
 * otherwise just the current and next two from {@link WaypointNavigator}. While navigating, also
 * draws the route itself as two connected lines - player to the current waypoint, then current to
 * next - rather than only listing them as text, and following the navigator's own looping past the
 * final waypoint back to the first. Uses the same depth-test-disabled line pipeline as
 * {@link EspRenderer} so everything reads through terrain.
 *
 * <p>This used to visibly lag behind the camera during fast movement for the same reason
 * {@link EspRenderer}'s fill did - see its javadoc and {@link #register()} - fixed by submitting
 * from {@code COLLECT_SUBMITS} instead of {@code AFTER_TRANSLUCENT_TERRAIN}. It was also briefly
 * ported to the vanilla Gizmos API to fix the lag a different way, but Gizmos submitted every
 * render frame get drained less often than that (looks tick-scoped, not frame-scoped), so
 * markers/lines/labels rendered duplicated several times over per tick instead - reverted back to
 * this SubmitNodeCollector approach rather than chase a tick-scoped submission path.
 */
public final class WaypointRenderer {
	private static final float LINE_WIDTH = 2.0f;
	private static final float PATH_LINE_WIDTH = 2.0f;
	// See EspGeometry.nearCameraOrigin - kept the same magnitude as TrackingRenderer's so the path's
	// first segment is equally immune to near-plane vanishing and head-bob swim.
	private static final float PATH_NEAR_OFFSET = 1.5f;
	private static final float TEXT_SCALE = 0.025f;
	// Vanilla's standard "fully lit" packed light value (15 sky, 15 block) - text should read the
	// same regardless of actual world lighting, matching the x-ray intent of the rest of the ESP.
	private static final int FULL_BRIGHT = 0xF000F0;

	private final ArashiConfig config;
	private final WaypointStore store;
	private final WaypointEditorState editorState;
	private final WaypointNavigator navigator;

	public WaypointRenderer(ArashiConfig config, WaypointStore store, WaypointEditorState editorState, WaypointNavigator navigator) {
		this.config = config;
		this.store = store;
		this.editorState = editorState;
		this.navigator = navigator;
	}

	// COLLECT_SUBMITS, not AFTER_TRANSLUCENT_TERRAIN - see EspRenderer.register() for why: submitting
	// this late missed the frame's own solid-feature draw pass and rendered a frame behind, which was
	// the camera lag during fast movement described in this class's javadoc above.
	public void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	private void render(LevelRenderContext context) {
		List<Waypoint> toRender = waypointsToRender();
		boolean navigating = !editorState.isEditing();
		Optional<Waypoint> current = navigating ? navigator.current() : Optional.empty();

		if (toRender.isEmpty() && current.isEmpty()) {
			return;
		}

		Vec3 camera = context.levelState().cameraRenderState.pos;
		SubmitNodeCollector collector = context.submitNodeCollector();
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);

		collector.submitCustomGeometry(poseStack, EspRenderTypes.LINES, (PoseStack.Pose pose, VertexConsumer outlineBuffer) -> {
			for (Waypoint waypoint : toRender) {
				int color = EspGeometry.withOpacity(colorFor(waypoint), 1.0f);
				AABB box = new AABB(waypoint.pos());
				EspGeometry.drawLineBox(pose, outlineBuffer, box, color, LINE_WIDTH);
			}

			current.ifPresent(waypoint -> drawNavigationPath(context, camera, pose, outlineBuffer, waypoint));
		});

		poseStack.popPose();

		if (config.waypointFloatingTextEnabled()) {
			for (Waypoint waypoint : toRender) {
				drawLabel(context, camera, waypoint);
			}
		}
	}

	/**
	 * The route itself, drawn as world-space lines rather than the old top-right text card: one
	 * segment from the player to the waypoint they're currently heading to, and one from there to
	 * the waypoint after it - which, thanks to {@link WaypointNavigator}'s looping, is the first
	 * waypoint again once {@code current} is the last one in the group.
	 */
	private void drawNavigationPath(LevelRenderContext context, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer, Waypoint current) {
		Vec3 origin = EspGeometry.nearCameraOrigin(camera, context.levelState().cameraRenderState.orientation, PATH_NEAR_OFFSET);
		Vec3 currentCenter = Vec3.atCenterOf(current.pos());
		int currentColor = EspGeometry.withOpacity(colorFor(current), 1.0f);
		EspGeometry.edge(pose, buffer, (float) origin.x, (float) origin.y, (float) origin.z,
				(float) currentCenter.x, (float) currentCenter.y, (float) currentCenter.z, currentColor, PATH_LINE_WIDTH);

		navigator.next().ifPresent(next -> {
			Vec3 nextCenter = Vec3.atCenterOf(next.pos());
			int nextColor = EspGeometry.withOpacity(colorFor(next), 1.0f);
			EspGeometry.edge(pose, buffer, (float) currentCenter.x, (float) currentCenter.y, (float) currentCenter.z,
					(float) nextCenter.x, (float) nextCenter.y, (float) nextCenter.z, nextColor, PATH_LINE_WIDTH);
		});
	}

	private void drawLabel(LevelRenderContext context, Vec3 camera, Waypoint waypoint) {
		Vec3 labelPos = Vec3.atCenterOf(waypoint.pos()).add(0, 1.3, 0);
		String text = "#" + waypoint.order() + " " + waypoint.type().label();
		int color = colorFor(waypoint);

		Font font = Minecraft.getInstance().font;
		int textWidth = font.width(text);
		FormattedCharSequence sequence = FormattedCharSequence.forward(text, Style.EMPTY);

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(labelPos.x - camera.x, labelPos.y - camera.y, labelPos.z - camera.z);
		poseStack.mulPose(context.levelState().cameraRenderState.orientation);
		poseStack.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

		context.submitNodeCollector().submitText(poseStack, -textWidth / 2f, 0, sequence, false,
				Font.DisplayMode.SEE_THROUGH, FULL_BRIGHT, color, 0, 0);

		poseStack.popPose();
	}

	private List<Waypoint> waypointsToRender() {
		if (editorState.isEditing()) {
			return store.get(editorState.editingGroupId()).map(WaypointGroup::waypoints).orElse(List.of());
		}

		return Stream.of(navigator.current(), navigator.next(), navigator.nextNext())
				.flatMap(Optional::stream)
				.toList();
	}

	private int colorFor(Waypoint waypoint) {
		return switch (waypoint.type()) {
			case PICKOBULUS -> config.waypointPickobulusColor();
			case ETHERWARP -> config.waypointEtherwarpColor();
		};
	}
}
