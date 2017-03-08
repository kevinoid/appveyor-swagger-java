package name.kevinlocke.appveyor.testutils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;

import org.testng.Reporter;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.logging.HttpLoggingInterceptor.Level;

import name.kevinlocke.appveyor.ApiClient;

public final class TestApiClient {
	private static final ApiClient testApiClient;

	static {
		String apiToken = System.getenv("APPVEYOR_API_TOKEN");
		if (apiToken == null || apiToken.trim().length() == 0) {
			throw new AssertionError("$APPVEYOR_API_TOKEN must be set");
		}

		testApiClient = new ApiClient();

		// Log the API endpoint being used
		StringBuilder apiEndpointMsg = new StringBuilder(
				"Using API endpoint: ");
		String basePath = testApiClient.getBasePath();
		apiEndpointMsg.append(basePath);
		try {
			String apiHost = new URI(basePath).getHost();
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

		testApiClient.setApiKeyPrefix("Bearer");
		testApiClient.setApiKey(apiToken);

		OkHttpClient httpClient = testApiClient.getHttpClient();
		List<Interceptor> interceptors = httpClient.interceptors();

		String swaggerUrl = TestApiClient.class.getResource("/swagger.yaml")
				.toString();
		// Swagger20Parser requires file URLs to start with file://
		// https://github.com/swagger-api/swagger-parser/pull/374
		if (!swaggerUrl.startsWith("file://")) {
			swaggerUrl = swaggerUrl.replace("file:", "file://");
		}
		interceptors.add(new SwaggerRequestValidatorInterceptor(swaggerUrl));

		// Note: Must be set after validator interceptor is added to print
		// before any validation errors are thrown.
		ReporterHttpLogger httpLogger = new ReporterHttpLogger();
		ConcurrentHttpLoggingInterceptor logInterceptor = new ConcurrentHttpLoggingInterceptor(
				httpLogger);
		logInterceptor.setLevel(Level.BODY);
		interceptors.add(logInterceptor);

		testApiClient.setJSON(new AssertLosslessJson(testApiClient));
	}

	public static ApiClient getTestApiClient() {
		return testApiClient;
	}

	// Prevent instantiation
	private TestApiClient() {
	}
}
