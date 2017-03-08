package name.kevinlocke.appveyor.testutils.json;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Gson TypeAdapter for Date type
 */
public class DateTypeAdapter extends TypeAdapter<Date> {

	private final SimpleDateFormat dateFormat = new SimpleDateFormat(
			"yyyy-MM-dd");
	private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ss.SSSZ");

	@Override
	public void write(JsonWriter out, Date date) throws IOException {
		if (date == null) {
			out.nullValue();
		} else {
			out.value(dateTimeFormat.format(date));
		}
	}

	@Override
	public Date read(JsonReader in) throws IOException {
		switch (in.peek()) {
		case NULL:
			in.nextNull();
			return null;
		default:
			String date = in.nextString();
			try {
				return date.indexOf('T') >= 0 ? dateTimeFormat.parse(date)
						: dateFormat.parse(date);
			} catch (ParseException e) {
				throw new RuntimeException(e);
			}
		}
	}
}