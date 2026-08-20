package dev.duetigh.arashirender.view;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Arashi-render has no notion of the mod-side scan UUID - it only ever sees a compact string or a
 * file containing one. Saved views are scoped instead to a hash of that string's compressed bytes,
 * so re-loading the exact same scan (via clipboard or file) always resolves to the same view set.
 */
public final class ScanIdentity {
	private ScanIdentity() {
	}

	public static String forCompactString(String compactString) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(compactString.strip().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 8);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
