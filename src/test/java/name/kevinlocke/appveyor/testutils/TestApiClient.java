package name.kevinlocke.appveyor.testutils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testng.Reporter;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.logging.HttpLoggingInterceptor.Level;

import name.kevinlocke.appveyor.ApiClient;
import name.kevinlocke.appveyor.EnhancedApiClient;

public final class TestApiClient {
	private static String getEnvRequired(String name) {
		String value = System.getenv(name);
		if (value != null) {
			value = value.trim();
			if (value.length() > 0) {
				return value;
			}
		}

		throw new RuntimeException(
				name + " environment variable must be set and not empty");
	}

	/**
	 * Gets an ApiClient configured for testing with a token and account name
	 * from given environment variables.
	 *
	 * @param apiTokenVar Name of environment variable with the bearer token to
	 *                    use for API Authorization.
	 * @param accountName Name of environment variable with the account name for
	 *                    user-level (v2) API, null or empty string for
	 *                    non-user-level (v1) API.
	 * @return ApiClient with logging and assertion checking suitable for tests.
	 */
	public static ApiClient getApiClientFromEnv(String apiTokenVar,
			String accountNameVar) {
		String apiToken = getEnvRequired(apiTokenVar);

		String accountName = null;
		if (apiToken.startsWith("v2.")) {
			accountName = getEnvRequired(accountNameVar);
		}

		return getApiClient(apiToken, accountName);
	}

	/**
	 * Gets an ApiClient configured for testing with a given token and account
	 * name.
	 *
	 * @param apiToken    Bearer token to use for API Authorization.
	 * @param accountName Account name for user-level (v2) API, null or empty
	 *                    string for non-user-level (v1) API.
	 * @return ApiClient with logging and assertion checking suitable for tests.
	 */
	public static ApiClient getApiClient(String apiToken, String accountName) {
		EnhancedApiClient apiClient = new EnhancedApiClient();

		// Add account name to base URL, if specified
		String origBaseUrl = apiClient.getBasePath();
		String baseUrl = origBaseUrl;
		if (accountName != null && accountName.length() > 0) {
			String accountNameEnc;
			try {
				accountNameEnc = URLEncoder
						.encode(accountName, StandardCharsets.UTF_8.name())
						.replace("+", "%20");
			} catch (UnsupportedEncodingException ex) {
				throw new RuntimeException("Error URL-encoding account name",
						ex);
			}

			baseUrl += "/account/" + accountNameEnc;
			apiClient.setBasePath(baseUrl);
		}

		// Log the API endpoint being used
		StringBuilder apiEndpointMsg =
				new StringBuilder("Using API endpoint: ");
		apiEndpointMsg.append(baseUrl);
		try {
			String apiHost = new URI(baseUrl).getHost();
			InetAddress[] addresses = InetAddress.getAllByName(apiHost);
			apiEndpointMsg.append(" (");
			for (InetAddress address : addresses) {
				apiEndpointMsg.append(address.getHostAddress()).append(", ");
			}
			apiEndpointMsg.delete(apiEndpointMsg.length() - 2,
					apiEndpointMsg.length());
			apiEndpointMsg.append(')');
		} catch (UnknownHostException e) {
			Reporter.log("Unable to resolve API host: " + e, 1, true);
		} catch (URISyntaxException e) {
			Reporter.log("Unable to parse API basepath: " + e, 1, true);
		}
		Reporter.log(apiEndpointMsg.toString(), 1, true);

		apiClient.setApiKeyPrefix("Bearer");
		apiClient.setApiKey(apiToken);

		OkHttpClient httpClient = apiClient.getHttpClient();

		// The API can be very slow under load. Set generous timeout values.
		httpClient.setConnectTimeout(30, TimeUnit.SECONDS);
		httpClient.setReadTimeout(2, TimeUnit.MINUTES);

		List<Interceptor> interceptors = httpClient.interceptors();

		URL swaggerUrl = TestApiClient.class.getResource("/swagger.yaml");
		String swaggerUrlOrContent = baseUrl == origBaseUrl
				? swaggerUrl.toString()
				: readModifiedSwaggerSpec(swaggerUrl, origBaseUrl, baseUrl);
		interceptors.add(
				new SwaggerRequestValidatorInterceptor(swaggerUrlOrContent));

		// Note: Must be set after validator interceptor is added to print
		// before any validation errors are thrown.
		ReporterHttpLogger httpLogger = new ReporterHttpLogger();
		ConcurrentHttpLoggingInterceptor logInterceptor =
				new ConcurrentHttpLoggingInterceptor(httpLogger);
		logInterceptor.setLevel(Level.BODY);
		interceptors.add(logInterceptor);

		apiClient.setJSON(new AssertLosslessJson(apiClient));

		return apiClient;
	}

	private static String readModifiedSwaggerSpec(URL swaggerUrl,
			String origBaseUrl, String newBaseUrl) {
		String swaggerContent;
		try (InputStream swaggerStream = swaggerUrl.openStream()) {
			swaggerContent = IOUtils.readAllAsString(swaggerStream,
					StandardCharsets.UTF_8.name());
		} catch (IOException ex) {
			throw new RuntimeException("Error reading swagger.yaml", ex);
		}

		String origBasePath, newBasePath;
		try {
			origBasePath = new URL(origBaseUrl).getPath();
			newBasePath = new URL(newBaseUrl).getPath();
		} catch (MalformedURLException ex) {
			throw new RuntimeException("Bad base URL", ex);
		}

		// FIXME: Should parse YAML, edit, then re-serialize. Non-trivial.
		// Swagger uses Jackson with mapping to non-serializable classes.
		// Simple string replacement is good enough for now.
		Pattern basePathPattern = Pattern.compile("^(basePath:\\s*[\"']?)"
				+ Pattern.quote(origBasePath) + "([\"']?\\s*)$",
				Pattern.MULTILINE);
		Matcher matcher = basePathPattern.matcher(swaggerContent);
		if (!matcher.find()) {
			throw new RuntimeException("Can't find basePath in swagger.yaml");
		}
		return swaggerContent.substring(0, matcher.start()) + matcher.group(1)
				+ newBasePath + matcher.group(2)
				+ swaggerContent.substring(matcher.end());
	}

	// Prevent instantiation
	private TestApiClient() {
	}
}
