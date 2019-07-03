package name.kevinlocke.appveyor.testutils;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.testng.Reporter;

import name.kevinlocke.appveyor.ApiClient;
import name.kevinlocke.appveyor.EnhancedApiClient;
import name.kevinlocke.appveyor.auth.Authentication;
import name.kevinlocke.appveyor.auth.HttpBearerAuth;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor.Level;

public final class TestApiClient {
	/**
	 * Gets an ApiClient configured for testing with a given token.
	 *
	 * @param apiToken Bearer token to use for API Authorization.
	 * @return ApiClient with logging and assertion checking suitable for tests.
	 */
	public static ApiClient getApiClient(String apiToken) {
		EnhancedApiClient apiClient = new EnhancedApiClient();

		// Log the API endpoint being used
		String baseUrl = apiClient.getBasePath();
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

		if (apiToken != null && apiToken.length() > 0) {
			Authentication apiTokenAuth =
					apiClient.getAuthentication("apiTokenV2");
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
				authentications.put("apiTokenV2", bearerAuth);

				try {
					Field authsField =
							ApiClient.class.getDeclaredField("authentications");
					authsField.setAccessible(true);
					authsField.set(apiClient, authentications);
				} catch (IllegalArgumentException | IllegalAccessException
						| NoSuchFieldException | SecurityException e) {
					throw new RuntimeException("Unable to set authentications",
							e);
				}
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

		httpClientBuilder.addInterceptor(
				new SwaggerRequestValidatorInterceptor(specUrl.toString()));

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

	// Prevent instantiation
	private TestApiClient() {}
}
