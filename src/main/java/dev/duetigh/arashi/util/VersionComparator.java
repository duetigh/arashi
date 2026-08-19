package dev.duetigh.arashi.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares dotted numeric version strings segment by segment (e.g. Minecraft's
 * {@code year.drop.patch} scheme or ordinary semver-like mod versions), instead of
 * lexicographic string comparison, so that {@code 26.2} sorts above {@code 26.1.11}.
 */
public final class VersionComparator {
	private static final Pattern LEADING_DIGITS = Pattern.compile("^\\d+");

	private VersionComparator() {
	}

	/**
	 * @return negative if {@code a < b}, positive if {@code a > b}, zero if equal.
	 */
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
