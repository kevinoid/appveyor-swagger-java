package name.kevinlocke.appveyor.testutils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.testng.Reporter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import name.kevinlocke.appveyor.ApiClient;
import name.kevinlocke.appveyor.EnhancedApiClient;
import name.kevinlocke.appveyor.auth.Authentication;
import name.kevinlocke.appveyor.auth.HttpBearerAuth;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor.Level;

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
	 *        use for API Authorization.
	 * @param accountName Name of environment variable with the account name for
	 *        user-level (v2) API, null or empty string for non-user-level (v1)
	 *        API.
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
	 * @param apiToken Bearer token to use for API Authorization.
	 * @param accountName Account name for user-level (v2) API, null or empty
	 *        string for non-user-level (v1) API.
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

			// Remove default account, if present
			int accountPos = baseUrl.indexOf("/account/");
			if (accountPos >= 0) {
				baseUrl = baseUrl.substring(0, accountPos);
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

		Authentication apiTokenAuth = apiClient.getAuthentication("apiToken");
		assert apiTokenAuth != null;
		if (apiTokenAuth instanceof HttpBearerAuth) {
			((HttpBearerAuth) apiTokenAuth).setBearerToken(apiToken);
		} else {
			// okhttp-gson doesn't support bearer in released versions
			// https://github.com/OpenAPITools/openapi-generator/pull/3236
			// Changing authentications is not permitted by API.
			// Set using reflection.

			HttpBearerAuth bearerAuth = new HttpBearerAuth("bearer");
			bearerAuth.setBearerToken(apiToken);

			HashMap<String, Authentication> authentications =
					new HashMap<>(apiClient.getAuthentications());
			authentications.put("apiToken", bearerAuth);

			try {
				Field authsField =
						ApiClient.class.getDeclaredField("authentications");
				authsField.setAccessible(true);
				authsField.set(apiClient, authentications);
			} catch (IllegalArgumentException | IllegalAccessException
					| NoSuchFieldException | SecurityException e) {
				throw new RuntimeException("Unable to set authentications", e);
			}
		}

		// The API can be very slow under load. Set generous timeout values.
		OkHttpClient.Builder httpClientBuilder =
				apiClient.getHttpClient().newBuilder();

		httpClientBuilder.connectTimeout(30, TimeUnit.SECONDS);
		httpClientBuilder.readTimeout(2, TimeUnit.MINUTES);

		URL specUrl = TestApiClient.class.getResource("/openapi.json");
		if (specUrl == null) {
			throw new RuntimeException("Unable to get openapi.json resource");
		}

		String specUrlOrContent;
		try {
			specUrlOrContent = baseUrl == origBaseUrl ? specUrl.toString()
					: readSpecWithBaseUrl(specUrl, baseUrl);
		} catch (IOException e) {
			throw new RuntimeException("Unable to read openapi.json", e);
		}

		httpClientBuilder.addInterceptor(
				new SwaggerRequestValidatorInterceptor(specUrlOrContent));

		// Note: Must be set after validator interceptor is added to print
		// before any validation errors are thrown.
		ReporterHttpLogger httpLogger = new ReporterHttpLogger();
		ConcurrentHttpLoggingInterceptor logInterceptor =
				new ConcurrentHttpLoggingInterceptor(httpLogger);
		logInterceptor.setLevel(Level.BODY);
		logInterceptor.redactHeader("Authorization");
		logInterceptor.redactHeader("Proxy-Authenticate");
		logInterceptor.redactHeader("Proxy-Authorization");
		logInterceptor.redactHeader("WWW-Authenticate");
		httpClientBuilder.addInterceptor(logInterceptor);

		apiClient.setHttpClient(httpClientBuilder.build());

		apiClient.setJSON(new AssertLosslessJson());

		return apiClient;
	}

	private static String readSpecWithBaseUrl(URL specUrl, String baseUrl)
			throws IOException {
		JsonObject specObject;
		try (InputStreamReader specReader = new InputStreamReader(
				specUrl.openStream(), StandardCharsets.UTF_8)) {
			specObject = (JsonObject) new JsonParser().parse(specReader);
		}

		JsonPrimitive openapi = specObject.getAsJsonPrimitive("openapi");
		if (openapi != null) {
			// OpenAPI 3 or later
			JsonArray servers = specObject.getAsJsonArray("servers");
			for (JsonElement server : servers) {
				((JsonObject) server).addProperty("url", baseUrl);
			}
		} else {
			// OpenAPI 2 fka Swagger
			String basePath;
			try {
				basePath = new URL(baseUrl).getPath();
			} catch (MalformedURLException ex) {
				throw new RuntimeException("Bad base URL", ex);
			}
			specObject.addProperty("basePath", basePath);
		}

		return specObject.toString();
	}

	// Prevent instantiation
	private TestApiClient() {}
}
