package dev.duetigh.arashirender.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/**
 * Checks GitHub Releases for a newer arashi-render build than the one currently running. arashi-render
 * ships in the same release/tag as the Fabric mod (see release.yml) but has its own independent
 * gradle.properties version, so "current version" here isn't that number - it's the release tag this
 * build was packaged from, baked in at jpackage time by the {@code generateVersionResource} Gradle
 * task (see build.gradle) reading the {@code -PreleaseTag} CI passes. A plain {@code ./gradlew run}
 * dev build has no tag, reports "dev", and never shows as out of date.
 */
public final class UpdateChecker {
	private static final Gson GSON = new Gson();
	private static final String REPO = "duetigh/arashi";
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final String VERSION_RESOURCE = "/arashi-render-version.txt";
	private static final String DEV_VERSION = "dev";

	private UpdateChecker() {
	}

	/** {@code changelog} is the release's raw GitHub markdown body, shown as-is (not rendered). */
	public record UpdateInfo(String currentVersion, String newVersion, String releaseUrl, String changelog) {
	}

	public static String currentVersion() {
		try (InputStream in = UpdateChecker.class.getResourceAsStream(VERSION_RESOURCE)) {
			if (in == null) {
				return DEV_VERSION;
			}

			String tag = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
			return tag.isEmpty() ? DEV_VERSION : tag;
		} catch (IOException e) {
			return DEV_VERSION;
		}
	}

	/** Runs off the calling thread; never blocks the caller. Only reports an {@link UpdateInfo} if a newer release exists. */
	public static void checkAsync(Consumer<UpdateInfo> onUpdateAvailable) {
		String current = currentVersion();

		if (DEV_VERSION.equals(current)) {
			return;
		}

		Thread.ofVirtual().name("arashi-render-update-check").start(() -> {
			try {
				check(current, onUpdateAvailable);
			} catch (Exception ignored) {
				// Best-effort - a failed update check should never disrupt startup.
			}
		});
	}

	private static void check(String currentVersion, Consumer<UpdateInfo> onUpdateAvailable) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api.github.com/repos/" + REPO + "/releases/latest"))
				.header("Accept", "application/vnd.github+json")
				.timeout(TIMEOUT)
				.GET()
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() != 200) {
			return;
		}

		Release release = GSON.fromJson(response.body(), Release.class);

		if (release == null || release.tagName == null || !VersionComparator.isNewer(release.tagName, currentVersion)) {
			return;
		}

		onUpdateAvailable.accept(new UpdateInfo(currentVersion, release.tagName, release.htmlUrl,
				release.body != null && !release.body.isBlank() ? release.body : "(no changelog provided)"));
	}

	private static final class Release {
		@SerializedName("tag_name")
		String tagName;
		@SerializedName("html_url")
		String htmlUrl;
		String body;
	}
}
