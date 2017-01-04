package name.kevinlocke.appveyor.testutils;

import java.io.IOException;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.logging.HttpLoggingInterceptor;
import com.squareup.okhttp.logging.HttpLoggingInterceptor.Level;
import com.squareup.okhttp.logging.HttpLoggingInterceptor.Logger;

/**
 * An HttpLoggingInterceptor which logs requests and responses together,
 * particularly useful with concurrent requests.
 */
public class ConcurrentHttpLoggingInterceptor implements Interceptor {
	protected final HttpLoggingInterceptor loggingInterceptor;
	protected final BufferedHttpLogger bufferedLogger;

	public ConcurrentHttpLoggingInterceptor() {
		this(Logger.DEFAULT);
	}

	public ConcurrentHttpLoggingInterceptor(Logger logger) {
		bufferedLogger = new BufferedHttpLogger(logger);
		loggingInterceptor = new HttpLoggingInterceptor(bufferedLogger);
	}

	/** Sets the level at which this interceptor logs. */
	public HttpLoggingInterceptor setLevel(Level level) {
		return loggingInterceptor.setLevel(level);
	}

	/** Gets the level at which this interceptor logs. */
	public Level getLevel() {
		return loggingInterceptor.getLevel();
	}

	@Override
	public Response intercept(Chain chain) throws IOException {
		try {
			return loggingInterceptor.intercept(chain);
		} finally {
			bufferedLogger.flush();
		}
	}
}
