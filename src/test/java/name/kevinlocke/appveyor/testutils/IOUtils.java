package name.kevinlocke.appveyor.testutils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Utility functions for input/output operations. */
public final class IOUtils {
	private static ByteArrayOutputStream readAllAsByteArrayOutputStream(
			InputStream inputStream) throws IOException {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int length;
		while ((length = inputStream.read(buffer)) != -1) {
			result.write(buffer, 0, length);
		}
		return result;
	}

	/**
	 * Reads all content from an InputStream as a String.
	 *
	 * Best performing implementation from https://stackoverflow.com/a/35446009
	 *
	 * @param inputStream Stream to read.
	 * @param charsetName Name of charset to decode the content.
	 * @return Decoded content from inputStream.
	 * @throws IOException If a read error occurs.
	 */
	public static String readAllAsString(InputStream inputStream,
			String charsetName) throws IOException {
		return readAllAsByteArrayOutputStream(inputStream)
				.toString(charsetName);
	}

	private IOUtils() {
	}
}
