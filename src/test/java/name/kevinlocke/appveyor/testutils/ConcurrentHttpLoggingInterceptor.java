/*
 * Copyright (C) 2015 Square, Inc.
 * Copyright (C) 2017-2019 Kevin Locke <kevin@kevinlocke.name>
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
 *
 * Based on
 * https://github.com/square/okhttp/blob/5f4a2642/okhttp-logging-interceptor/src/main/java/okhttp3/logging/HttpLoggingInterceptor.java
 * Changes:
 * - Add a request UUID to make it easier to match response to request.
 * - Use a single logger.log per request/response to avoid interleaving.
 *
 * @formatter:off
 */
package name.kevinlocke.appveyor.testutils;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Connection;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import okhttp3.logging.HttpLoggingInterceptor.Logger;

import okio.Buffer;
import okio.BufferedSource;
import okio.GzipSource;

/**
 * An OkHttp interceptor which logs request and response information. Can be applied as an
 * {@linkplain OkHttpClient#interceptors() application interceptor} or as a {@linkplain
 * OkHttpClient#networkInterceptors() network interceptor}. <p> The format of the logs created by
 * this class should not be considered stable and may change slightly between releases. If you need
 * a stable logging format, use your own interceptor.
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

  private volatile Set<String> headersToRedact = Collections.emptySet();

  public void redactHeader(String name) {
    Set<String> newHeadersToRedact = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    newHeadersToRedact.addAll(headersToRedact);
    newHeadersToRedact.add(name);
    headersToRedact = newHeadersToRedact;
  }

  private volatile Level level = Level.NONE;

  /** Change the level at which this interceptor logs. */
  public ConcurrentHttpLoggingInterceptor setLevel(Level level) {
    if (level == null) throw new NullPointerException("level == null. Use Level.NONE instead.");
    this.level = level;
    return this;
  }

  public Level getLevel() {
    return level;
  }

  @Override public Response intercept(Chain chain) throws IOException {
    Level level = this.level;

    Request request = chain.request();
    if (level == Level.NONE) {
      return chain.proceed(request);
    }

    boolean logBody = level == Level.BODY;
    boolean logHeaders = logBody || level == Level.HEADERS;

    RequestBody requestBody = request.body();
    boolean hasRequestBody = requestBody != null;

    UUID requestId = UUID.randomUUID();

    Connection connection = chain.connection();
    StringBuilder requestMessage = new StringBuilder("--> ")
        .append(requestId).append('\n')
		.append(request.method())
        .append(' ').append(request.url());
    if (connection != null) {
      requestMessage.append(' ').append(connection.protocol());
    }
    if (!logHeaders && hasRequestBody) {
      requestMessage.append(" (").append(requestBody.contentLength()).append("-byte body)");
    }
    requestMessage.append('\n');

    if (logHeaders) {
      if (hasRequestBody) {
        // Request body headers are only present when installed as a network interceptor. Force
        // them to be included (when available) so there values are known.
        if (requestBody.contentType() != null) {
          requestMessage.append("Content-Type: ").append(requestBody.contentType()).append('\n');
        }
        if (requestBody.contentLength() != -1) {
          requestMessage.append("Content-Length: ").append(requestBody.contentLength()).append('\n');
        }
      }

      Headers headers = request.headers();
      for (int i = 0, count = headers.size(); i < count; i++) {
        String name = headers.name(i);
        // Skip headers from the request body as they are explicitly logged above.
        if (!"Content-Type".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name)) {
          logHeader(requestMessage, headers, i);
        }
      }

      if (!logBody || !hasRequestBody) {
        requestMessage.append("--> END ").append(request.method())
            .append(' ').append(requestId).append('\n');
      } else if (bodyHasUnknownEncoding(request.headers())) {
        requestMessage.append("--> END ").append(request.method())
            .append(' ').append(requestId)
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
              .append("\n--> END ").append(request.method())
              .append(' ').append(requestId)
              .append(" (").append(requestBody.contentLength()).append("-byte body)\n");
        } else {
          requestMessage.append("--> END ").append(request.method())
              .append(' ').append(requestId)
              .append(" (binary ")
              .append(requestBody.contentLength()).append("-byte body omitted)\n");
        }
      }
    }

    // Log the message, excluding the trailing '\n'
    logger.log(requestMessage.substring(0, requestMessage.length() - 1));

    long startNs = System.nanoTime();
    Response response;
    try {
      response = chain.proceed(request);
    } catch (Exception e) {
      logger.log("<-- " + requestId + "HTTP FAILED: " + e);
      throw e;
    }
    long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

    ResponseBody responseBody = response.body();
    long contentLength = responseBody.contentLength();
    String bodySize = contentLength != -1 ? contentLength + "-byte" : "unknown-length";
    StringBuilder responseMessage = new StringBuilder("<-- ")
        .append(requestId).append(' ')
        .append(response.code());
    if (!response.message().isEmpty()) {
        responseMessage.append(' ').append(response.message());
    }
    responseMessage
        .append(' ').append(response.request().url())
        .append(" (").append(tookMs).append("ms");
    if (!logHeaders) {
      responseMessage.append(", ").append(bodySize).append(" body");
    }
    responseMessage.append(")\n");

    if (logHeaders) {
      Headers headers = response.headers();
      for (int i = 0, count = headers.size(); i < count; i++) {
        logHeader(responseMessage, headers, i);
      }

      if (!logBody || !HttpHeaders.hasBody(response)) {
        responseMessage.append("<-- END HTTP\n");
      } else if (bodyHasUnknownEncoding(response.headers())) {
        responseMessage.append("<-- END HTTP (encoded body omitted)\n");
      } else {
        BufferedSource source = responseBody.source();
        source.request(Long.MAX_VALUE); // Buffer the entire body.
        Buffer buffer = source.getBuffer();

        Long gzippedLength = null;
        if ("gzip".equalsIgnoreCase(headers.get("Content-Encoding"))) {
          gzippedLength = buffer.size();
          try (GzipSource gzippedResponseBody = new GzipSource(buffer.clone())) {
            buffer = new Buffer();
            buffer.writeAll(gzippedResponseBody);
          }
        }

        Charset charset = UTF8;
        MediaType contentType = responseBody.contentType();
        if (contentType != null) {
          charset = contentType.charset(UTF8);
        }

        if (!isPlaintext(buffer)) {
          responseMessage.append('\n')
              .append("<-- END HTTP (binary ").append(buffer.size()).append("-byte body omitted)");
          logger.log(responseMessage.toString());
          return response;
        }

        if (contentLength != 0) {
          responseMessage.append('\n')
              .append(buffer.clone().readString(charset))
              .append('\n');
        }

        if (gzippedLength != null) {
          responseMessage.append("<-- END HTTP (").append(buffer.size()).append("-byte, ")
              .append(gzippedLength).append("-gzipped-byte body)\n");
        } else {
          responseMessage.append("<-- END HTTP (").append(buffer.size()).append("-byte body)\n");
        }
      }
    }

    // Log the message, excluding the trailing '\n'
    logger.log(responseMessage.substring(0, responseMessage.length() - 1));

    return response;
  }

  private void logHeader(StringBuilder responseMessage, Headers headers, int i) {
    String value = headersToRedact.contains(headers.name(i)) ? "██" : headers.value(i);
    responseMessage.append(headers.name(i)).append(": ").append(value).append('\n');
  }

  /**
   * Returns true if the body in question probably contains human readable text. Uses a small sample
   * of code points to detect unicode control characters commonly used in binary file signatures.
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
        if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
          return false;
        }
      }
      return true;
    } catch (EOFException e) {
      return false; // Truncated UTF-8 sequence.
    }
  }

  private static boolean bodyHasUnknownEncoding(Headers headers) {
    String contentEncoding = headers.get("Content-Encoding");
    return contentEncoding != null
        && !contentEncoding.equalsIgnoreCase("identity")
        && !contentEncoding.equalsIgnoreCase("gzip");
  }
}
