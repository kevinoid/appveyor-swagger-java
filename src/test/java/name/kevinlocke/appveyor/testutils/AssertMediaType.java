package name.kevinlocke.appveyor.testutils;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.Reporter;

/**
 * Assertions for media types.
 */
public class AssertMediaType {
	protected static String guessMediaType(InputStream is, Path path)
			throws IOException {
		// Marking is required for URLConnection.guessContentTypeFromStream
		assert is.markSupported() : "InputStream must support marking";

		String mediaType = null;
		if (path != null) {
			mediaType = Files.probeContentType(path);
			Reporter.log("Files.probeContentType(" + path + "): " + mediaType,
					4);
		}
		if (mediaType == null && is != null) {
			mediaType = URLConnection.guessContentTypeFromStream(is);
			Reporter.log("URLConnection.guessContentTypeFromStream(" + path
					+ "): " + mediaType, 4);
		}
		if (mediaType == null && path != null) {
			mediaType = URLConnection.guessContentTypeFromName(path.toString());
			Reporter.log("URLConnection.guessContentTypeFromName(" + path
					+ "): " + mediaType, 4);
		}
		return mediaType;
	}

	protected static String trimParams(String mediaType) {
		if (mediaType == null) {
			return mediaType;
		}

		int semiInd = mediaType.indexOf(';');
		if (semiInd > 0) {
			mediaType = mediaType.substring(0, semiInd);
		}

		return mediaType.trim();
	}

	public static void assertMediaType(InputStream is, Path path,
			String expectedType) throws IOException {
		String contentType = trimParams(guessMediaType(is, path));
		assertEquals(contentType, expectedType);
	}

	public static void assertMediaType(Path path, String expectedType)
			throws IOException {
		try (BufferedInputStream bis = new BufferedInputStream(
				Files.newInputStream(path))) {
			assertMediaType(bis, path, expectedType);
		}
	}

	public static void assertIsPng(Path path) throws IOException {
		assertMediaType(path, "image/png");
	}

	public static void assertIsSvg(InputStream is, Path path)
			throws IOException {
		String contentType = trimParams(guessMediaType(is, path));

		// URLConnection.guessContentTypeFromStream recognizes XML from the XML
		// declaration and doesn't recognize SVG specifically. Check xmlns.
		if (contentType == null || contentType.equals("application/xml")
				|| contentType.equals("text/xml")) {
			char[] searchBuf = new char[512];
			is.mark(searchBuf.length);
			// Need to use standard single-byte charset compatible with ASCII.
			InputStreamReader reader = new InputStreamReader(is,
					StandardCharsets.ISO_8859_1);
			int nRead;
			try {
				nRead = reader.read(searchBuf);
			} finally {
				is.reset();
			}
			String searchStr = new String(searchBuf, 0, nRead);
			// Note: There are lots of ways to make this check tighter.
			// This works for the current use case for now. Improve as needed.
			assertTrue(searchStr.indexOf("http://www.w3.org/2000/svg") >= 0,
					"Content contains SVG namespace");
			return;
		}

		assertEquals(contentType, "image/svg+xml");
	}

	public static void assertIsSvg(Path path) throws IOException {
		try (BufferedInputStream bis = new BufferedInputStream(
				Files.newInputStream(path))) {
			assertIsSvg(bis, path);
		}
	}

	// Prevent direct instantiation
	protected AssertMediaType() {
	}
}
