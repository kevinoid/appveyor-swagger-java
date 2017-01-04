package name.kevinlocke.appveyor.testutils;

import java.util.ArrayDeque;
import java.util.Queue;

import com.squareup.okhttp.logging.HttpLoggingInterceptor.Logger;

/** A Logger which buffers its messages until flushed. */
public class BufferedHttpLogger implements AutoCloseable, Logger {
	protected final Logger logger;
	protected final Queue<String> logQueue;

	/** Creates a BufferedHttpLogger which writes to a given Logger when
	 * flushed.
	 * @param logger Logger to which messages are written when flushed.
	 */
	public BufferedHttpLogger(Logger logger) {
		this.logger = logger;
		this.logQueue = new ArrayDeque<String>();
	}

	/** Flushes any queued messages. */
	@Override
	public void close() {
		flush();
	}

	/** Writes any queued messages to the backing logger. */
	public void flush() {
		String combined;
		synchronized (logQueue) {
			combined = String.join("\n", logQueue);
			logQueue.clear();
		}
		logger.log(combined);
	}

	/** Queue a log message which will be written when flushed. */
	@Override
	public void log(String message) {
		synchronized (logQueue) {
			logQueue.add(message);
		}
	}
}
