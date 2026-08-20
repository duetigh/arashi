package dev.duetigh.arashirender.texture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL33.*;

/**
 * Extracts block textures from a Minecraft version jar / resource pack zip and uploads them as GL
 * textures on demand, caching one GL texture id per resolved texture path (see
 * {@link BlockTextureMap}). {@code textureIdFor} returns null - not throws - for any block whose
 * texture isn't in the current source, so the caller can fall back to a flat color.
 */
public final class TextureLoader {
	private ZipFile zip;
	private String sourcePath;
	private final Map<String, Integer> textureIdsByPath = new HashMap<>();
	private final Map<String, Integer> textureIdsByBlockId = new HashMap<>();

	public String sourcePath() {
		return sourcePath;
	}

	/** Switches the active texture source, dropping any previously loaded GL textures. */
	public void setSource(String zipOrJarPath) throws IOException {
		close();
		this.zip = new ZipFile(zipOrJarPath);
		this.sourcePath = zipOrJarPath;
	}

	public boolean hasSource() {
		return zip != null;
	}

	/** GL texture id for a block's representative texture, lazily loaded and cached; null if unavailable. */
	public Integer textureIdFor(String blockId) {
		if (zip == null) {
			return null;
		}

		if (textureIdsByBlockId.containsKey(blockId)) {
			return textureIdsByBlockId.get(blockId);
		}

		String texturePath = BlockTextureMap.texturePathFor(blockId);
		Integer glId = loadTexture(texturePath);
		textureIdsByBlockId.put(blockId, glId);
		return glId;
	}

	private Integer loadTexture(String texturePath) {
		if (textureIdsByPath.containsKey(texturePath)) {
			return textureIdsByPath.get(texturePath);
		}

		Integer glId = decodeAndUpload(texturePath);
		textureIdsByPath.put(texturePath, glId);
		return glId;
	}

	private Integer decodeAndUpload(String texturePath) {
		ZipEntry entry = zip.getEntry("assets/minecraft/textures/" + texturePath);

		if (entry == null) {
			return null;
		}

		ByteBuffer fileBytes = null;

		try (InputStream in = zip.getInputStream(entry)) {
			fileBytes = readAllBytesDirect(in);
			fileBytes.flip();

			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBufferTrio dims = new IntBufferTrio(stack);
				STBImage.stbi_set_flip_vertically_on_load(true);
				ByteBuffer pixels = STBImage.stbi_load_from_memory(fileBytes, dims.width, dims.height, dims.channels, 4);

				if (pixels == null) {
					return null;
				}

				int width = dims.width.get(0);
				int height = dims.height.get(0);

				int texture = glGenTextures();
				glBindTexture(GL_TEXTURE_2D, texture);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
				glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
				STBImage.stbi_image_free(pixels);
				glBindTexture(GL_TEXTURE_2D, 0);
				return texture;
			}
		} catch (IOException e) {
			return null;
		} finally {
			if (fileBytes != null) {
				MemoryUtil.memFree(fileBytes);
			}
		}
	}

	private static ByteBuffer readAllBytesDirect(InputStream in) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		int read;

		while ((read = in.read(chunk)) != -1) {
			buffer.write(chunk, 0, read);
		}

		byte[] bytes = buffer.toByteArray();
		ByteBuffer direct = MemoryUtil.memAlloc(bytes.length);
		direct.put(bytes);
		return direct;
	}

	public void close() {
		if (zip != null) {
			try {
				zip.close();
			} catch (IOException ignored) {
				// Best-effort cleanup when switching sources.
			}
		}

		for (Integer glId : textureIdsByPath.values()) {
			if (glId != null) {
				glDeleteTextures(glId);
			}
		}

		zip = null;
		sourcePath = null;
		textureIdsByPath.clear();
		textureIdsByBlockId.clear();
	}

	private static final class IntBufferTrio {
		final java.nio.IntBuffer width;
		final java.nio.IntBuffer height;
		final java.nio.IntBuffer channels;

		IntBufferTrio(MemoryStack stack) {
			width = stack.mallocInt(1);
			height = stack.mallocInt(1);
			channels = stack.mallocInt(1);
		}
	}
}
