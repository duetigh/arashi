package dev.duetigh.arashi.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Camera-relative box/line vertex helpers shared by every world-space overlay renderer
 * ({@link EspRenderer}, {@link TrackingRenderer}, {@link WaypointRenderer}) so the vertex math
 * for a filled box, a textured box, and a wireframe box/line only lives in one place.
 */
final class EspGeometry {
	/** Face-exposure index order used by the {@code boolean[6]} overloads below: -X, +X, -Y, +Y, -Z, +Z. */
	static final int NEG_X = 0, POS_X = 1, NEG_Y = 2, POS_Y = 3, NEG_Z = 4, POS_Z = 5;
	private static final boolean[] ALL_EXPOSED = {true, true, true, true, true, true};

	private EspGeometry() {
	}

	// A line whose start point sits exactly at the camera (or is offset *toward whatever it's
	// pointing at*) risks landing on or behind the near clip plane, which vanishes the whole line
	// rather than just clipping the hidden part - it depends entirely on where that target happens
	// to be relative to the view direction, which for tracking/navigation lines is arbitrary. Offset
	// along the camera's own forward vector instead, which is always safely in front regardless of
	// target direction.
	static Vec3 nearCameraOrigin(Vec3 cameraPos, Quaternionf cameraOrientation, float offset) {
		Vector3f forward = cameraOrientation.transform(new Vector3f(0.0f, 0.0f, -1.0f));
		return cameraPos.add(forward.x * offset, forward.y * offset, forward.z * offset);
	}

	static int withOpacity(int rgb, float alpha) {
		float r = ((rgb >> 16) & 0xFF) / 255.0f;
		float g = ((rgb >> 8) & 0xFF) / 255.0f;
		float b = (rgb & 0xFF) / 255.0f;
		return ARGB.colorFromFloat(alpha, r, g, b);
	}

	static void drawFilledBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
		drawFilledBox(pose, buffer, box, color, ALL_EXPOSED);
	}

	// Debug-filled-box's pipeline back-face culls, so every quad below is wound counter-clockwise
	// as seen from outside the box (each face's normal points outward). A face is skipped when
	// `exposed` says a same-type match sits directly against it - see EspRenderer, which computes
	// that per matched block so two touching boxes of the same block don't both draw (and thus
	// double-render, looking darker/thicker) the internal wall between them.
	static void drawFilledBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, boolean[] exposed) {
		if (exposed[NEG_Z]) { // Front (-Z)
			buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ).setColor(color);
			buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.minZ).setColor(color);
			buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.minZ).setColor(color);
			buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ).setColor(color);
		}

		if (exposed[POS_Z]) { // Back (+Z)
			buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ).setColor(color);
			buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.maxZ).setColor(color);
			buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.maxZ).setColor(color);
			buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.maxZ).setColor(color);
		}

		if (exposed[NEG_X]) { // Left (-X)
			buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.maxZ).setColor(color);
			buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.maxZ).setColor(color);
			buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.minZ).setColor(color);
			buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ).setColor(color);
		}

		if (exposed[POS_X]) { // Right (+X)
			buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ).setColor(color);
			buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.minZ).setColor(color);
			buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.maxZ).setColor(color);
			buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ).setColor(color);
		}

		if (exposed[POS_Y]) { // Top (+Y)
			buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.minZ).setColor(color);
			buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.maxZ).setColor(color);
			buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.maxZ).setColor(color);
			buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.minZ).setColor(color);
		}

		if (exposed[NEG_Y]) { // Bottom (-Y)
			buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.maxZ).setColor(color);
			buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ).setColor(color);
			buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ).setColor(color);
			buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ).setColor(color);
		}
	}

	static void drawTexturedBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, TextureAtlasSprite sprite, int color) {
		drawTexturedBox(pose, buffer, box, sprite, color, ALL_EXPOSED);
	}

	// Every face below cycles its 4 corners in the same "low, up, up-across, low-across" order as
	// drawFilledBox, so the same 4-corner UV cycle (bottom-left, top-left, top-right, bottom-right
	// of the sprite) tiles correctly on all 6 faces without per-face UV bookkeeping.
	static void drawTexturedBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, TextureAtlasSprite sprite, int color, boolean[] exposed) {
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();

		if (exposed[NEG_Z]) { // Front (-Z)
			texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.minZ, u0, v1, color);
			texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.minZ, u0, v0, color);
			texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.minZ, u1, v0, color);
			texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.minZ, u1, v1, color);
		}

		if (exposed[POS_Z]) { // Back (+Z)
			texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.maxZ, u0, v1, color);
			texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.maxZ, u0, v0, color);
			texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.maxZ, u1, v0, color);
			texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.maxZ, u1, v1, color);
		}

		if (exposed[NEG_X]) { // Left (-X)
			texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.maxZ, u0, v1, color);
			texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.maxZ, u0, v0, color);
			texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.minZ, u1, v0, color);
			texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.minZ, u1, v1, color);
		}

		if (exposed[POS_X]) { // Right (+X)
			texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.minZ, u0, v1, color);
			texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.minZ, u0, v0, color);
			texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.maxZ, u1, v0, color);
			texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.maxZ, u1, v1, color);
		}

		if (exposed[POS_Y]) { // Top (+Y)
			texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.minZ, u0, v1, color);
			texVertex(pose, buffer, (float) box.minX, (float) box.maxY, (float) box.maxZ, u0, v0, color);
			texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.maxZ, u1, v0, color);
			texVertex(pose, buffer, (float) box.maxX, (float) box.maxY, (float) box.minZ, u1, v1, color);
		}

		if (exposed[NEG_Y]) { // Bottom (-Y)
			texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.maxZ, u0, v1, color);
			texVertex(pose, buffer, (float) box.minX, (float) box.minY, (float) box.minZ, u0, v0, color);
			texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.minZ, u1, v0, color);
			texVertex(pose, buffer, (float) box.maxX, (float) box.minY, (float) box.maxZ, u1, v1, color);
		}
	}

	private static void texVertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color) {
		buffer.addVertex(pose, x, y, z).setUv(u, v).setColor(color);
	}

	static void drawLineBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width) {
		drawLineBox(pose, buffer, box, color, width, ALL_EXPOSED);
	}

	// Each edge borders exactly 2 of the 6 faces; it's drawn if at least one of those 2 is exposed,
	// so a fully-interior edge (both bordering faces hidden by same-type neighbors) is skipped while
	// silhouette/boundary edges of a touching cluster still render.
	static void drawLineBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width, boolean[] exposed) {
		float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
		float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;

		// Bottom face edges (each borders -Y and one side face)
		if (exposed[NEG_Y] || exposed[NEG_Z]) {
			edge(pose, buffer, minX, minY, minZ, maxX, minY, minZ, color, width);
		}

		if (exposed[NEG_Y] || exposed[POS_X]) {
			edge(pose, buffer, maxX, minY, minZ, maxX, minY, maxZ, color, width);
		}

		if (exposed[NEG_Y] || exposed[POS_Z]) {
			edge(pose, buffer, maxX, minY, maxZ, minX, minY, maxZ, color, width);
		}

		if (exposed[NEG_Y] || exposed[NEG_X]) {
			edge(pose, buffer, minX, minY, maxZ, minX, minY, minZ, color, width);
		}

		// Top face edges (each borders +Y and one side face)
		if (exposed[POS_Y] || exposed[NEG_Z]) {
			edge(pose, buffer, minX, maxY, minZ, maxX, maxY, minZ, color, width);
		}

		if (exposed[POS_Y] || exposed[POS_X]) {
			edge(pose, buffer, maxX, maxY, minZ, maxX, maxY, maxZ, color, width);
		}

		if (exposed[POS_Y] || exposed[POS_Z]) {
			edge(pose, buffer, maxX, maxY, maxZ, minX, maxY, maxZ, color, width);
		}

		if (exposed[POS_Y] || exposed[NEG_X]) {
			edge(pose, buffer, minX, maxY, maxZ, minX, maxY, minZ, color, width);
		}

		// Vertical edges (each borders two side faces)
		if (exposed[NEG_X] || exposed[NEG_Z]) {
			edge(pose, buffer, minX, minY, minZ, minX, maxY, minZ, color, width);
		}

		if (exposed[POS_X] || exposed[NEG_Z]) {
			edge(pose, buffer, maxX, minY, minZ, maxX, maxY, minZ, color, width);
		}

		if (exposed[POS_X] || exposed[POS_Z]) {
			edge(pose, buffer, maxX, minY, maxZ, maxX, maxY, maxZ, color, width);
		}

		if (exposed[NEG_X] || exposed[POS_Z]) {
			edge(pose, buffer, minX, minY, maxZ, minX, maxY, maxZ, color, width);
		}
	}

	static void edge(PoseStack.Pose pose, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, int color, float width) {
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
