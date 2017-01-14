package name.kevinlocke.appveyor.testutils;

import static org.testng.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assertions for media types.
 */
public final class AssertMediaType {
	private static String guessMediaType(InputStream is, Path path)
			throws IOException {
		String mediaType = null;
		if (path != null) {
			mediaType = Files.probeContentType(path);
		}
		if (mediaType == null && is != null) {
			mediaType = URLConnection.guessContentTypeFromStream(is);
		}
		if (mediaType == null && path != null) {
			mediaType = URLConnection.guessContentTypeFromName(path.toString());
		}
		return mediaType;
	}

	private static String trimParams(String mediaType) {
		if (mediaType == null) {
			return mediaType;
		}

		int semiInd = mediaType.indexOf(';');
		if (semiInd > 0) {
			mediaType = mediaType.substring(0, semiInd);
		}

		return mediaType.trim();
	}

	public static void assertIsPng(InputStream is, Path path)
			throws IOException {
		String contentType = trimParams(guessMediaType(is, path));
		assertEquals(contentType, "image/png");
	}

	public static void assertIsPng(Path path) throws IOException {
		try (BufferedInputStream bis = new BufferedInputStream(
				Files.newInputStream(path))) {
			assertIsPng(bis, path);
		}
	}

	public static void assertIsSvg(InputStream is, Path path)
			throws IOException {
		String contentType = trimParams(guessMediaType(is, path));
		assertEquals(contentType, "image/svg+xml");
	}

	public static void assertIsSvg(Path path) throws IOException {
		try (BufferedInputStream bis = new BufferedInputStream(
				Files.newInputStream(path))) {
			assertIsSvg(bis, path);
		}
	}

	// Prevent instantiation
	private AssertMediaType() {
	}
}
