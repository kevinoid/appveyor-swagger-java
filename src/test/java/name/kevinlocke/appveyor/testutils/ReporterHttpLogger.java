package name.kevinlocke.appveyor.testutils;

import org.testng.Reporter;

import com.squareup.okhttp.logging.HttpLoggingInterceptor.Logger;

/** Logger which writes to the TestNG Reporter. */
public class ReporterHttpLogger implements Logger {
	@Override
	public void log(String message) {
		Reporter.log(message, 1, true);
	}
}
