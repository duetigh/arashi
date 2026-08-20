package dev.duetigh.arashirender.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares dotted numeric version strings segment by segment (e.g. {@code v0.1.10} vs {@code v0.2}),
 * instead of lexicographic string comparison. Ported from the mod's {@code VersionComparator} -
 * arashi-render is a fully separate Gradle build with no dependency on the mod's classes, so this is
 * duplicated rather than shared.
 */
final class VersionComparator {
	private static final Pattern LEADING_DIGITS = Pattern.compile("^\\d+");

	private VersionComparator() {
	}

	public static int compare(String a, String b) {
		int[] segmentsA = parse(a);
		int[] segmentsB = parse(b);
		int length = Math.max(segmentsA.length, segmentsB.length);

		for (int i = 0; i < length; i++) {
			int partA = i < segmentsA.length ? segmentsA[i] : 0;
			int partB = i < segmentsB.length ? segmentsB[i] : 0;

			if (partA != partB) {
				return Integer.compare(partA, partB);
			}
		}

		return 0;
	}

	public static boolean isNewer(String candidate, String current) {
		return compare(candidate, current) > 0;
	}

	private static int[] parse(String version) {
		String trimmed = version.strip();

		if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
			trimmed = trimmed.substring(1);
		}

		String[] rawSegments = trimmed.split("\\.");
		int[] result = new int[rawSegments.length];

		for (int i = 0; i < rawSegments.length; i++) {
			Matcher matcher = LEADING_DIGITS.matcher(rawSegments[i]);
			result[i] = matcher.find() ? Integer.parseInt(matcher.group()) : 0;
		}

		return result;
	}
}
