package name.kevinlocke.appveyor.testutils;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import name.kevinlocke.appveyor.ApiTest;

/**
 * Utility functions for dealing with resources.
 */
public class Resources {

	/**
	 * Gets the content of a resource as a String.
	 *
	 * @param name Name of the resource.
	 * @return Content of the resource named {@code name}.
	 * @throws RuntimeException If an error occurs while reading the resource.
	 */
	public static String getAsString(String name) {
		ByteArrayOutputStream resourceData = new ByteArrayOutputStream();
		try (InputStream stream = ApiTest.class.getResourceAsStream(name)) {
			if (stream == null) {
				throw new FileNotFoundException(name);
			}

			byte[] buffer = new byte[4096];
			int nread = stream.read(buffer);
			while (nread >= 0) {
				resourceData.write(buffer, 0, nread);
				nread = stream.read(buffer);
			}
		} catch (IOException e) {
			// TODO: Convert to UncheckedIOException for Java 8
			throw new RuntimeException(e);
		}
		return resourceData.toString();
	}

	// Prevent instantiation
	private Resources() {
	}
}
