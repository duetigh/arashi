package dev.duetigh.arashirender.texture;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Locates a local Minecraft client jar to use as the default texture source. Checks the vanilla
 * launcher plus the third-party launchers most of this mod's (Fabric/Modrinth) audience actually
 * uses - Modrinth App, Prism Launcher/MultiMC, ATLauncher, and CurseForge - since very few of them
 * run the stock Mojang launcher. Every check is a defensive existence check, so a wrong guess about
 * a launcher's install layout just means it finds nothing there, not a crash.
 */
public final class TextureSource {
	/** Directory names skipped while scanning a launcher root - large and never contain client jars. */
	private static final Set<String> SKIP_DIR_NAMES = Set.of(
			"mods", "resourcepacks", "shaderpacks", "saves", "screenshots", "logs", "crash-reports", "config",
			"assets", "resources", "cache", "cache-http", "download-cache", "meta", "themes", "translations");

	private TextureSource() {
	}

	/** Best-guess {@code .minecraft/versions} folder for this OS, or null if it doesn't exist. */
	public static String defaultVersionsDir() {
		File vanillaRoot = vanillaRoot();
		File versions = new File(vanillaRoot, "versions");
		return versions.isDirectory() ? versions.getAbsolutePath() : null;
	}

	/**
	 * The most recently modified client jar found across the vanilla launcher and the common
	 * third-party launcher installs on this machine, or null if none is found. Recency of the jar
	 * file itself (written once, the first time a version is launched) is a reasonable stand-in for
	 * "most recently played version/instance" without needing to parse any launcher's own metadata.
	 */
	public static String findLatestInstalledJar() {
		List<File> candidates = new ArrayList<>();

		collectVersionsStyleJars(new File(vanillaRoot(), "versions"), candidates);

		for (File root : thirdPartyLauncherRoots()) {
			collectVersionsStyleJars(root, candidates);
		}

		for (File root : prismLikeRoots()) {
			collectPrismLibraryJars(root, candidates);
		}

		File newest = null;

		for (File jar : candidates) {
			if (newest == null || jar.lastModified() > newest.lastModified()) {
				newest = jar;
			}
		}

		return newest != null ? newest.getAbsolutePath() : null;
	}

	private static File vanillaRoot() {
		String os = System.getProperty("os.name", "").toLowerCase();
		String home = System.getProperty("user.home");

		if (os.contains("win")) {
			String appData = System.getenv("APPDATA");
			return new File(appData != null ? appData : home, ".minecraft");
		} else if (os.contains("mac")) {
			return Path.of(home, "Library", "Application Support", "minecraft").toFile();
		} else {
			return new File(home, ".minecraft");
		}
	}

	/** Data-directory roots for launchers that lay out instances like the vanilla launcher (a "versions" folder with {@code <id>/<id>.jar} inside). */
	private static List<File> thirdPartyLauncherRoots() {
		String os = System.getProperty("os.name", "").toLowerCase();
		String home = System.getProperty("user.home");
		String appData = System.getenv("APPDATA");
		List<File> roots = new ArrayList<>();

		if (os.contains("win")) {
			String base = appData != null ? appData : home;
			roots.add(new File(base, "com.modrinth.theseus")); // Modrinth App
			roots.add(new File(base, "ModrinthApp"));
			roots.add(new File(base, "atlauncher")); // ATLauncher
			roots.add(new File(home, "curseforge/minecraft/Instances")); // CurseForge - no official mac/linux app
		} else if (os.contains("mac")) {
			Path support = Path.of(home, "Library", "Application Support");
			roots.add(support.resolve("ModrinthApp").toFile());
			roots.add(support.resolve("com.modrinth.theseus").toFile());
			roots.add(new File(home, ".atlauncher"));
			roots.add(support.resolve("ATLauncher").toFile());
			roots.add(support.resolve("curseforge/minecraft/Instances").toFile());
		} else {
			roots.add(Path.of(home, ".local", "share", "ModrinthApp").toFile());
			roots.add(Path.of(home, ".local", "share", "com.modrinth.theseus").toFile());
			roots.add(new File(home, ".atlauncher"));
			roots.add(new File(home, "curseforge/minecraft/Instances"));
		}

		return roots;
	}

	/** Data-directory roots for launchers (Prism Launcher, its predecessor MultiMC) that cache client jars once under a shared {@code libraries/com/mojang/minecraft/} path rather than per-instance. */
	private static List<File> prismLikeRoots() {
		String os = System.getProperty("os.name", "").toLowerCase();
		String home = System.getProperty("user.home");
		String appData = System.getenv("APPDATA");
		List<File> roots = new ArrayList<>();

		if (os.contains("win")) {
			String base = appData != null ? appData : home;
			roots.add(new File(base, "PrismLauncher"));
			roots.add(new File(base, "MultiMC"));
		} else if (os.contains("mac")) {
			Path support = Path.of(home, "Library", "Application Support");
			roots.add(support.resolve("PrismLauncher").toFile());
			roots.add(support.resolve("MultiMC").toFile());
		} else {
			roots.add(Path.of(home, ".local", "share", "PrismLauncher").toFile());
			roots.add(Path.of(home, ".local", "share", "MultiMC").toFile());
		}

		return roots;
	}

	/** Walks a launcher root (bounded depth, skipping known-heavy folders) for any {@code versions/<id>/<id>.jar}. */
	private static void collectVersionsStyleJars(File root, List<File> out) {
		if (!root.isDirectory()) {
			return;
		}

		try {
			Files.walkFileTree(root.toPath(), Set.of(), 8, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					String name = dir.getFileName().toString();

					if (!dir.equals(root.toPath()) && SKIP_DIR_NAMES.contains(name.toLowerCase())) {
						return FileVisitResult.SKIP_SUBTREE;
					}

					if (name.equals("versions")) {
						File[] versionFolders = dir.toFile().listFiles(File::isDirectory);

						if (versionFolders != null) {
							for (File versionFolder : versionFolders) {
								File jar = new File(versionFolder, versionFolder.getName() + ".jar");

								if (jar.isFile()) {
									out.add(jar);
								}
							}
						}

						return FileVisitResult.SKIP_SUBTREE;
					}

					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ignored) {
			// Unreadable directory somewhere under root - skip it, we still have other candidates.
		}
	}

	/** Collects every {@code libraries/com/mojang/minecraft/<id>/minecraft-<id>-client.jar} under a Prism/MultiMC-style root. */
	private static void collectPrismLibraryJars(File root, List<File> out) {
		File mojangDir = new File(root, "libraries/com/mojang/minecraft");
		File[] versionFolders = mojangDir.listFiles(File::isDirectory);

		if (versionFolders == null) {
			return;
		}

		for (File versionFolder : versionFolders) {
			File[] jars = versionFolder.listFiles((dir, name) -> name.endsWith("-client.jar"));

			if (jars != null) {
				for (File jar : jars) {
					out.add(jar);
				}
			}
		}
	}
}
