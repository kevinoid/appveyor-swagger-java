/*
 * Copyright (C) 2015 Square, Inc.
 * Copyright (C) 2017 Kevin Locke <kevin@kevinlocke.name>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package name.kevinlocke.appveyor.testutils;

import com.squareup.okhttp.Connection;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.internal.http.HttpEngine;
import com.squareup.okhttp.logging.HttpLoggingInterceptor.Level;
import com.squareup.okhttp.logging.HttpLoggingInterceptor.Logger;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okio.Buffer;
import okio.BufferedSource;

/**
 * An OkHttp interceptor which logs request and response information. Can be
 * applied as an {@linkplain OkHttpClient#interceptors() application
 * interceptor} or as a {@linkplain OkHttpClient#networkInterceptors() network
 * interceptor}.
 * <p>
 * The format of the logs created by this class should not be considered stable
 * and may change slightly between releases. If you need a stable logging
 * format, use your own interceptor.
 */
public final class ConcurrentHttpLoggingInterceptor implements Interceptor {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	public ConcurrentHttpLoggingInterceptor() {
		this(Logger.DEFAULT);
	}

	public ConcurrentHttpLoggingInterceptor(Logger logger) {
		this.logger = logger;
	}

	private final Logger logger;

	private volatile Level level = Level.NONE;

	/** Change the level at which this interceptor logs. */
	public ConcurrentHttpLoggingInterceptor setLevel(Level level) {
		if (level == null)
			throw new NullPointerException(
					"level == null. Use Level.NONE instead.");
		this.level = level;
		return this;
	}

	public Level getLevel() {
		return level;
	}

	@Override
	public Response intercept(Chain chain) throws IOException {
		Level level = this.level;

		Request request = chain.request();
		if (level == Level.NONE) {
			return chain.proceed(request);
		}

		boolean logBody = level == Level.BODY;
		boolean logHeaders = logBody || level == Level.HEADERS;

		RequestBody requestBody = request.body();

		Connection connection = chain.connection();
		Protocol protocol = connection != null ? connection.getProtocol()
				: Protocol.HTTP_1_1;
		UUID requestId = UUID.randomUUID();
		StringBuilder requestMessage = new StringBuilder("--> ")
				.append(requestId).append('\n').append(request.method())
				.append(' ').append(request.httpUrl()).append(' ')
				.append(protocol);
		if (!logHeaders && requestBody != null) {
			requestMessage.append(" (").append(requestBody.contentLength())
					.append("-byte body)");
		}
		requestMessage.append('\n');

		if (logHeaders) {
			if (requestBody != null) {
				// Request body headers are only present when installed as a
				// network interceptor. Force
				// them to be included (when available) so there values are
				// known.
				if (requestBody.contentType() != null) {
					requestMessage.append("Content-Type: ")
							.append(requestBody.contentType()).append('\n');
				}
				if (requestBody.contentLength() != -1) {
					requestMessage.append("Content-Length: ")
							.append(requestBody.contentLength()).append('\n');
				}
			}

			Headers headers = request.headers();
			for (int i = 0, count = headers.size(); i < count; i++) {
				String name = headers.name(i);
				if ("Authorization".equalsIgnoreCase(name)
						|| "Proxy-Authenticate".equalsIgnoreCase(name)
						|| "Proxy-Authorization".equalsIgnoreCase(name)
						|| "WWW-Authenticate".equalsIgnoreCase(name)) {
					requestMessage.append(name).append(": *****\n");
				}
				// Skip headers from the request body as they are explicitly
				// logged above.
				else if (!"Content-Type".equalsIgnoreCase(name)
						&& !"Content-Length".equalsIgnoreCase(name)) {
					requestMessage.append(name).append(": ")
							.append(headers.value(i)).append('\n');
				}
			}

			if (!logBody || requestBody == null) {
				requestMessage.append("--> END ").append(requestId)
						.append('\n');
			} else if (bodyEncoded(request.headers())) {
				requestMessage.append("--> END ").append(requestId)
						.append(" (encoded body omitted)").append('\n');
			} else {
				Buffer buffer = new Buffer();
				requestBody.writeTo(buffer);

				Charset charset = UTF8;
				MediaType contentType = requestBody.contentType();
				if (contentType != null) {
					charset = contentType.charset(UTF8);
				}

				requestMessage.append('\n');
				if (isPlaintext(buffer)) {
					requestMessage.append(buffer.readString(charset))
							.append("\n--> END ").append(requestId).append(" (")
							.append(requestBody.contentLength())
							.append("-byte body)\n");
				} else {
					requestMessage.append("--> END ").append(requestId)
							.append(" (binary ")
							.append(requestBody.contentLength())
							.append("-byte body omitted)\n");
				}
			}
		}

		logger.log(requestMessage.substring(0, requestMessage.length() - 1));

		long startNs = System.nanoTime();
		Response response;
		try {
			response = chain.proceed(request);
		} catch (Exception e) {
			logger.log("<-- " + requestId + "HTTP FAILED: " + e);
			throw e;
		}
		long tookMs = TimeUnit.NANOSECONDS
				.toMillis(System.nanoTime() - startNs);

		ResponseBody responseBody = response.body();
		long contentLength = responseBody.contentLength();
		StringBuilder responseMessage = new StringBuilder("<-- ")
				.append(requestId).append(' ').append(response.request().url())
				.append(" (").append(tookMs).append("ms");
		if (!logHeaders) {
			responseMessage.append(", ");
			if (contentLength != -1) {
				responseMessage.append(contentLength).append("-byte");
			} else {
				responseMessage.append("unknown-length");
			}
			responseMessage.append(" body");
		}
		responseMessage.append(")\n");

		responseMessage.append(response.code()).append(' ')
				.append(response.message()).append('\n');

		if (logHeaders) {
			Headers headers = response.headers();
			for (int i = 0, count = headers.size(); i < count; i++) {
				responseMessage.append(headers.name(i)).append(": ")
						.append(headers.value(i)).append('\n');
			}

			if (!logBody || !HttpEngine.hasBody(response)) {
				responseMessage.append("<-- END HTTP\n");
			} else if (bodyEncoded(response.headers())) {
				responseMessage.append("<-- END HTTP (encoded body omitted)\n");
			} else {
				BufferedSource source = responseBody.source();
				source.request(Long.MAX_VALUE); // Buffer the entire body.
				Buffer buffer = source.buffer();

				Charset charset = UTF8;
				MediaType contentType = responseBody.contentType();
				if (contentType != null) {
					charset = contentType.charset(UTF8);
				}

				if (!isPlaintext(buffer)) {
					responseMessage.append('\n').append("<-- END HTTP (binary ")
							.append(buffer.size())
							.append("-byte body omitted)");
					logger.log(responseMessage.toString());
					return response;
				}

				if (contentLength != 0) {
					responseMessage.append('\n')
							.append(buffer.clone().readString(charset))
							.append('\n');
				}

				responseMessage.append("<-- END HTTP (").append(buffer.size())
						.append("-byte body)\n");
			}
		}

		logger.log(responseMessage.substring(0, responseMessage.length() - 1));
		return response;
	}

	/**
	 * Returns true if the body in question probably contains human readable
	 * text. Uses a small sample of code points to detect unicode control
	 * characters commonly used in binary file signatures.
	 */
	static boolean isPlaintext(Buffer buffer) {
		try {
			Buffer prefix = new Buffer();
			long byteCount = buffer.size() < 64 ? buffer.size() : 64;
			buffer.copyTo(prefix, 0, byteCount);
			for (int i = 0; i < 16; i++) {
				if (prefix.exhausted()) {
					break;
				}
				int codePoint = prefix.readUtf8CodePoint();
				if (Character.isISOControl(codePoint)
						&& !Character.isWhitespace(codePoint)) {
					return false;
				}
			}
			return true;
		} catch (EOFException e) {
			return false; // Truncated UTF-8 sequence.
		}
	}

	private boolean bodyEncoded(Headers headers) {
		String contentEncoding = headers.get("Content-Encoding");
		return contentEncoding != null
				&& !contentEncoding.equalsIgnoreCase("identity");
	}
}
