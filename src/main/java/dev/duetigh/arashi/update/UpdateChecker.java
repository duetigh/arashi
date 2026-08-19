package dev.duetigh.arashi.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import net.fabricmc.loader.api.FabricLoader;
import dev.duetigh.arashi.ArashiClient;
import dev.duetigh.arashi.util.VersionComparator;

/**
 * Checks GitHub Releases for a newer build of the mod, and if one exists, stages the new
 * jar next to the running one without touching the live {@code mods/arashi.jar} - the JVM
 * holds a lock on that file for as long as the game is running. A shutdown hook moves the
 * staged jar into place once the lock is released on the next natural game close.
 */
public final class UpdateChecker {
	private static final Gson GSON = new Gson();
	private static final String REPO = "duetigh/arashi";
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private UpdateChecker() {
	}

	/** Runs off the calling thread; never blocks the caller. Reports the new version string, if any, via the callback. */
	public static void checkAsync(Consumer<String> onUpdateAvailable) {
		Thread.ofVirtual().name("arashi-update-check").start(() -> {
			try {
				check(onUpdateAvailable);
			} catch (Exception e) {
				ArashiClient.LOGGER.warn("Arashi update check failed", e);
			}
		});
	}

	private static void check(Consumer<String> onUpdateAvailable) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api.github.com/repos/" + REPO + "/releases/latest"))
				.header("Accept", "application/vnd.github+json")
				.timeout(TIMEOUT)
				.GET()
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() != 200) {
			ArashiClient.LOGGER.info("Arashi update check: no release found (HTTP {})", response.statusCode());
			return;
		}

		Release release = GSON.fromJson(response.body(), Release.class);

		if (release == null || release.tagName == null) {
			return;
		}

		String currentVersion = FabricLoader.getInstance()
				.getModContainer("arashi")
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");

		if (!VersionComparator.isNewer(release.tagName, currentVersion)) {
			return;
		}

		Optional<Asset> jarAsset = release.assets == null ? Optional.empty() : release.assets.stream()
				.filter(a -> "arashi.jar".equals(a.name))
				.findFirst();

		if (jarAsset.isEmpty()) {
			ArashiClient.LOGGER.warn("Arashi update {} found but no arashi.jar asset was attached", release.tagName);
			onUpdateAvailable.accept(release.tagName);
			return;
		}

		stageUpdate(client, jarAsset.get().browserDownloadUrl);
		onUpdateAvailable.accept(release.tagName);
	}

	private static void stageUpdate(HttpClient client, String downloadUrl) throws IOException, InterruptedException {
		Path stagingDir = FabricLoader.getInstance().getGameDir().resolve("mods").resolve(".arashi-update");
		Files.createDirectories(stagingDir);
		Path stagedJar = stagingDir.resolve("arashi.jar");

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build();
		HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(stagedJar));

		if (response.statusCode() != 200) {
			ArashiClient.LOGGER.warn("Failed to download Arashi update: HTTP {}", response.statusCode());
			return;
		}

		Path liveJar = FabricLoader.getInstance().getGameDir().resolve("mods").resolve("arashi.jar");

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				Files.move(stagedJar, liveJar, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				// Best-effort: the file may still be locked by a still-shutting-down JVM; the
				// staged jar is left in place and will be retried on the next launch.
			}
		}, "arashi-update-swap"));
	}

	private static final class Release {
		@SerializedName("tag_name")
		String tagName;
		List<Asset> assets;
	}

	private static final class Asset {
		String name;
		@SerializedName("browser_download_url")
		String browserDownloadUrl;
	}
}
