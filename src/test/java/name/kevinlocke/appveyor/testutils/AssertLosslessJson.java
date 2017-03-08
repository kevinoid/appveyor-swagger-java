package name.kevinlocke.appveyor.testutils;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;

import org.joda.time.DateTime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import name.kevinlocke.appveyor.ApiClient;
import name.kevinlocke.appveyor.JSON;

/**
 * JSON serializer/deserializer which asserts that deserialization is lossless
 * (i.e. that the result can be serialized back to the same JSON).
 */
public class AssertLosslessJson extends JSON {
	// Java 8-compatible String.join
	private static String join(CharSequence delimiter,
			Iterable<? extends CharSequence> elements) {
		Objects.requireNonNull(delimiter);
		Objects.requireNonNull(elements);
		StringBuilder builder = new StringBuilder();
		for (CharSequence cs : elements) {
			builder.append(cs).append(delimiter);
		}
		return builder.substring(0, builder.length() - delimiter.length());
	}

	protected static void jsonEqualsFail(JsonElement expected,
			JsonElement actual, Deque<String> path) {
		throw new ComparisonFailure("JSON differs at " + join(".", path),
				expected, "==", actual);
	}

	/**
	 * Checks if two JSON values are dates which are equal to the millisecond.
	 * This is used to accommodate differences due to date-time formatting and
	 * the limited precision of the Java7 time types.
	 * @param expected Expected JSON value.
	 * @param actual Actual JSON value.
	 * @return {@code true} if {@code expected} and {@code actual} are date-time
	 * values which are equal to the millisecond.
	 */
	protected static boolean isJsonDateEquals(JsonElement expected,
			JsonElement actual) {
		if (!(expected instanceof JsonPrimitive)) {
			return false;
		}

		JsonPrimitive expectedPrimitive = (JsonPrimitive) expected;
		JsonPrimitive actualPrimitive = (JsonPrimitive) actual;
		if (!expectedPrimitive.isString() || !actualPrimitive.isString()) {
			return false;
		}

		String expectedValue = expectedPrimitive.getAsString();
		String actualValue = actualPrimitive.getAsString();
		/*
		 * java.time implementation try { OffsetDateTime expectedInstant =
		 * OffsetDateTime.parse(expectedValue,
		 * DateTimeFormatter.ISO_OFFSET_DATE_TIME); OffsetDateTime actualInstant
		 * = OffsetDateTime.parse(actualValue,
		 * DateTimeFormatter.ISO_OFFSET_DATE_TIME); // Require difference of
		 * less than 1 millisecond long nanos =
		 * ChronoUnit.NANOS.between(expectedInstant, actualInstant); return
		 * Math.abs(nanos) < 1000000; } catch (ArithmeticException e) { //
		 * Overflow in ChronoUnit.between() return false; } catch
		 * (DateTimeParseException e) { return false; }
		 */
		try {
			DateTime expectedInstant = DateTime.parse(expectedValue);
			DateTime actualInstant = DateTime.parse(actualValue);
			// Require difference of at-most 1 millisecond
			long millis = expectedInstant.getMillis()
					- actualInstant.getMillis();
			return Math.abs(millis) <= 1;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	protected static void assertJsonEquals(JsonElement expected,
			JsonElement actual, Deque<String> path) throws AssertionError {
		if (expected == actual) {
			return;
		}

		if (expected == null || actual == null) {
			if (expected != null || actual != null) {
				jsonEqualsFail(expected, actual, path);
			}
			return;
		}

		if (expected.getClass() != actual.getClass()) {
			jsonEqualsFail(expected, actual, path);
		}

		if (expected instanceof JsonArray) {
			JsonArray expectedArray = (JsonArray) expected;
			JsonArray actualArray = (JsonArray) actual;
			if (expectedArray.size() != actualArray.size()) {
				jsonEqualsFail(expected, actual, path);
			}

			for (int i = 0; i < expectedArray.size(); ++i) {
				path.addLast(Integer.toString(i));
				assertJsonEquals(expectedArray.get(i), actualArray.get(i),
						path);
				path.removeLast();
			}
		} else if (expected instanceof JsonObject) {
			JsonObject expectedObject = (JsonObject) expected;
			JsonObject actualObject = (JsonObject) actual;
			for (Map.Entry<String, JsonElement> entry : expectedObject
					.entrySet()) {
				String propertyName = entry.getKey();
				JsonElement actualValue = actualObject.get(propertyName);
				path.addLast(propertyName);
				assertJsonEquals(entry.getValue(), actualValue, path);
				path.removeLast();
			}
		} else if (!expected.equals(actual)
				&& !isJsonDateEquals(expected, actual)) {
			jsonEqualsFail(expected, actual, path);
		}
	}

	/**
	 * Asserts that two JSON elements are equal.
	 *
	 * This function does not consider the order of properties in an object to
	 * be significant. It also does not consider differences in date values
	 * (strings which match the RFC 3339 date-time pattern) of less than 1
	 * millisecond to be significant.
	 *
	 * @param expected The expected JSON element.
	 * @param actual The actual JSON element.
	 * @throws AssertionError If {@code actual} is not equal to
	 * {@code expected}.
	 */
	public static void assertJsonEquals(JsonElement expected,
			JsonElement actual) throws AssertionError {
		assertJsonEquals(expected, actual, new ArrayDeque<String>());
	}

	public AssertLosslessJson(ApiClient apiClient) {
		super(apiClient);
	}

	protected <T> void assertSameJson(String body, T obj) {
		JsonElement bodyJson = new JsonParser().parse(body);
		JsonElement resultJson = this.getGson().toJsonTree(obj);
		assertJsonEquals(bodyJson, resultJson);
	}

	@Override
	public <T> T deserialize(String body, Type returnType) {
		T result = super.deserialize(body, returnType);
		assertSameJson(body, result);
		return result;
	}
}
