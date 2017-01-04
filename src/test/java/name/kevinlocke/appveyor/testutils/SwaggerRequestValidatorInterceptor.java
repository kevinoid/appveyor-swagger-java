package name.kevinlocke.appveyor.testutils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.atlassian.oai.validator.SwaggerRequestResponseValidator;
import com.atlassian.oai.validator.model.Request.Method;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.report.ValidationReport.Message;
import com.atlassian.oai.validator.schema.SchemaValidator;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.internal.http.HttpEngine;

import okio.Buffer;
import okio.BufferedSource;

/**
 * An Interceptor which validates API request/response pairs against the Swagger
 * definition.
 */
public class SwaggerRequestValidatorInterceptor implements Interceptor {
	/**
	 * Pattern to match an ISO 8601 datetime with microsecond (or higher)
	 * precision as a JSON string value.
	 */
	protected static final Pattern ISO_DATE_TIME_MICRO_JSON = Pattern
			.compile("\"(?<date>[0-9]{4}-[0-9]{2}-[0-9]{2})"
					+ "T(?<time>[0-9]{2}:[0-9]{2}:[0-9]{2})"
					+ "\\.(?<msec>[0-9]{3})(?<unsec>[0-9]+)"
					+ "(?<tz>Z|(?:\\+|-)[0-9]{2}:[0-9]{2})\"");

	protected static Charset guessCharset(MediaType contentType) {
		if (contentType == null) {
			return StandardCharsets.UTF_8;
		}

		Charset charset = contentType.charset();
		if (charset == null) {
			// HTTP 1.1 default text charset is ISO-8859-1
			charset = contentType.type() == "text" ? StandardCharsets.ISO_8859_1
					: StandardCharsets.UTF_8;
		}
		return charset;
	}

	protected static Charset guessCharset(RequestBody requestBody) {
		return guessCharset(requestBody.contentType());
	}

	protected static Charset guessCharset(ResponseBody responseBody) {
		return guessCharset(responseBody.contentType());
	}

	/**
	 * Converts a ResponseBody to String in a way that allows it to be re-read
	 * by later interceptors and the caller.
	 *
	 * Code based on HttpLoggingInterceptor.
	 *
	 * @param responseBody
	 *            Response body to read.
	 * @return Response body as a String.
	 * @throws IOException
	 */
	protected static String responseBodyToString(ResponseBody responseBody)
			throws IOException {
		Charset charset = guessCharset(responseBody);
		BufferedSource source = responseBody.source();
		source.request(Long.MAX_VALUE); // Buffer the entire body.
		// Note: Content length may not be known until response is buffered
		if (responseBody.contentLength() == 0) {
			return "";
		}
		try (Buffer buffer = source.buffer().clone()) {
			return buffer.readString(charset);
		}
	}

	protected static boolean isJson(MediaType mediaType) {
		String subtype = mediaType.subtype();
		return subtype.equals("json") || subtype.equals("x-json")
				|| subtype.endsWith("+json");
	}

	protected static String responseBodyFixup(String responseBody) {
		// Times with more than millisecond precision do not validate
		// https://github.com/daveclayton/json-schema-validator/issues/166
		Matcher dateMatcher = ISO_DATE_TIME_MICRO_JSON.matcher(responseBody);
		String dateFixed = dateMatcher
				.replaceAll("\"${date}T${time}.${msec}${tz}\"");
		return dateFixed;
	}

	protected static com.atlassian.oai.validator.model.Response okhttpToValidatorResponse(
			Response response) throws IOException {
		SimpleResponse.Builder responseBuilder = new SimpleResponse.Builder(
				response.code());

		// It's difficult to determine if the response body is actually empty.
		// It returns a ResponseBody with .contentLength() == -1.
		// Use same method as HttpLoggingInterceptor
		if (HttpEngine.hasBody(response)) {
			ResponseBody responseBody = response.body();
			String responseBodyString = responseBodyToString(responseBody);
			responseBodyString = responseBodyFixup(responseBodyString);
			responseBuilder = responseBuilder.withBody(responseBodyString);
		}

		return responseBuilder.build();
	}

	protected static com.atlassian.oai.validator.model.Request okhttpToValidatorRequest(
			Request request) throws IOException {
		Method requestMethod = Method.valueOf(request.method());
		HttpUrl requestUrl = request.httpUrl();
		String requestPath = requestUrl.encodedPath();
		RequestBody requestBody = request.body();
		SimpleRequest.Builder requestBuilder = new SimpleRequest.Builder(
				requestMethod, requestPath);
		if (requestBody != null) {
			String requestBodyString = requestBodyToString(requestBody);
			requestBuilder = requestBuilder.withBody(requestBodyString);
		}
		for (int i = 0; i < requestUrl.querySize(); ++i) {
			String name = requestUrl.queryParameterName(i);
			String value = requestUrl.queryParameterValue(i);
			requestBuilder = requestBuilder.withQueryParam(name, value);
		}
		return requestBuilder.build();
	}

	protected static String requestBodyToString(RequestBody requestBody)
			throws IOException {
		Buffer buffer = new Buffer();
		requestBody.writeTo(buffer);
		Charset charset = guessCharset(requestBody);
		return buffer.readString(charset);
	}

	protected final SwaggerRequestResponseValidator validator;

	public SwaggerRequestValidatorInterceptor(String swaggerJsonUrlOrPayload) {
		// additionalProperties validation is broken for schemas with allOf
		// Disable it. AssertLosslessJson should cover it.
		LevelResolver levelResolver = LevelResolver.create()
				.withLevel(SchemaValidator.ADDITIONAL_PROPERTIES_KEY,
						ValidationReport.Level.IGNORE)
				.build();
		validator = SwaggerRequestResponseValidator
				.createFor(swaggerJsonUrlOrPayload)
				.withLevelResolver(levelResolver).build();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.squareup.okhttp.Interceptor#intercept(com.squareup.okhttp.Interceptor
	 * .Chain)
	 */
	@Override
	public Response intercept(Chain chain) throws IOException {
		Request okRequest = chain.request();
		Response okResponse = chain.proceed(okRequest);

		RequestBody requestBody = okRequest.body();
		MediaType requestType = requestBody != null ? requestBody.contentType()
				: null;
		ResponseBody responseBody = okResponse.body();
		MediaType responseType = responseBody != null
				? responseBody.contentType() : null;
		if ((requestType != null && !isJson(requestType))
				|| (responseType != null && !isJson(responseType))) {
			// Don't validate non-JSON requests or responses since their
			// handling is not well specified in the OpenAPI spec and
			// swagger-request-validator doesn't support file type.
			return okResponse;
		}

		com.atlassian.oai.validator.model.Request vRequest = okhttpToValidatorRequest(
				okRequest);
		com.atlassian.oai.validator.model.Response vResponse = okhttpToValidatorResponse(
				okResponse);

		ValidationReport report = validator.validate(vRequest, vResponse);
		List<Message> messages = report.getMessages();
		if (!messages.isEmpty()) {
			throw new ValidationException(messages);
		}

		return okResponse;
	}

}
