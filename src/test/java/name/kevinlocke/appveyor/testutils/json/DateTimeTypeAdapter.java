package name.kevinlocke.appveyor.testutils.json;

import java.io.IOException;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Gson TypeAdapter for Joda DateTime type
 */
public class DateTimeTypeAdapter extends TypeAdapter<DateTime> {

	private final DateTimeFormatter parseFormatter = ISODateTimeFormat
			.dateOptionalTimeParser();
	private final DateTimeFormatter printFormatter = ISODateTimeFormat
			.dateTime();

	@Override
	public void write(JsonWriter out, DateTime date) throws IOException {
		if (date == null) {
			out.nullValue();
		} else {
			out.value(printFormatter.print(date));
		}
	}

	@Override
	public DateTime read(JsonReader in) throws IOException {
		switch (in.peek()) {
		case NULL:
			in.nextNull();
			return null;
		default:
			String date = in.nextString();
			return parseFormatter.parseDateTime(date);
		}
	}
}