package name.kevinlocke.appveyor.testutils.json;

import java.io.IOException;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Gson TypeAdapter for Joda LocalDate type
 */
public class LocalDateTypeAdapter extends TypeAdapter<LocalDate> {

	private final DateTimeFormatter formatter = ISODateTimeFormat.date();

	@Override
	public void write(JsonWriter out, LocalDate date) throws IOException {
		if (date == null) {
			out.nullValue();
		} else {
			out.value(formatter.print(date));
		}
	}

	@Override
	public LocalDate read(JsonReader in) throws IOException {
		switch (in.peek()) {
		case NULL:
			in.nextNull();
			return null;
		default:
			String date = in.nextString();
			return formatter.parseLocalDate(date);
		}
	}
}