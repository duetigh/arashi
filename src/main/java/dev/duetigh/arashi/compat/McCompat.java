package dev.duetigh.arashi.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Bridges Minecraft client API shapes that differ between the game versions Arashi supports.
 * Mojang moved the "currently open screen" accessor from a public field on {@link Minecraft}
 * (26.1.2 and earlier) to a getter on {@code Minecraft.gui} (26.2 and later); this resolves
 * whichever shape is present once, on first use, and caches it as a MethodHandle. Add further
 * resolution attempts here - never at call sites - when a future version moves it again.
 */
public final class McCompat {
	private static final MethodHandle CURRENT_SCREEN_GETTER = resolveCurrentScreenGetter();

	private McCompat() {
	}

	public static Screen currentScreen(Minecraft client) {
		try {
			return (Screen) CURRENT_SCREEN_GETTER.invoke(client);
		} catch (Throwable t) {
			throw new IllegalStateException("Failed to read the current screen on this Minecraft version", t);
		}
	}

	private static MethodHandle resolveCurrentScreenGetter() {
		MethodHandles.Lookup lookup = MethodHandles.lookup();

		try {
			// 26.2+: Minecraft.gui.screen()
			Field guiField = Minecraft.class.getField("gui");
			Method screenMethod = guiField.getType().getMethod("screen");
			MethodHandle guiGetter = lookup.unreflectGetter(guiField);
			MethodHandle screenGetter = lookup.unreflect(screenMethod);
			return MethodHandles.filterReturnValue(guiGetter, screenGetter);
		} catch (ReflectiveOperationException ignored) {
			// Not on this version; fall through to the older shape.
		}

		try {
			// 26.1.2 and earlier: Minecraft.screen field
			Field screenField = Minecraft.class.getField("screen");
			return lookup.unreflectGetter(screenField);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(
					"Could not locate a compatible 'current screen' accessor on this Minecraft version", e);
		}
	}
}
