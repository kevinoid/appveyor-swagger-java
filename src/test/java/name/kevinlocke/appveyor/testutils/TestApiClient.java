package name.kevinlocke.appveyor.testutils;

import java.util.List;

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
