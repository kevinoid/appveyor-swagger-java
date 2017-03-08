package name.kevinlocke.appveyor.testutils;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;
import org.skyscreamer.jsonassert.comparator.DefaultComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;
import org.testng.Assert;

import com.google.gson.ExclusionStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import name.kevinlocke.appveyor.testutils.json.CommonPropertiesJSONComparator;
import name.kevinlocke.appveyor.testutils.json.DateTimeTypeAdapter;
import name.kevinlocke.appveyor.testutils.json.JsonOrgTreeWriter;
import name.kevinlocke.appveyor.testutils.json.LocalDateTypeAdapter;

/**
 * Assertions for model classes.
 *
 * Comparing model class instances is complicated by the fact that Swagger
 * Codegen does not define conversions between model classes (even for ones
 * which inherit via anyOf). It is further complicated by the amount of data in
 * each instance, making the reason for non-equality difficult to spot in
 * assertion failures. This class exists to address these issues.
 *
 * There are several libraries which address the issue of object property
 * comparison:
 * <ul>
 * <li>{@link org.apache.commons.lang3.builder.EqualsBuilder#reflectionEquals(Object, Object, boolean)}
 * can determine equality of instances via reflection, but does not provide
 * specific information about which properties were non-equal.</li>
 * <li>{@link com.nitorcreations.matchers.ReflectionEqualsMatcher} wraps
 * {@link org.apache.commons.lang3.builder.EqualsBuilder#reflectionEquals(Object, Object, boolean)}
 * as an assertion.</li>
 * <li>{@link org.hamcrest.beans.SamePropertyValuesAs} does shallow comparison
 * of bean properties, but only if the instance types are assignable.</li>
 * <li>{@link org.unitils.reflectionassert.ReflectionAssert#assertReflectionEquals(Object, Object, org.unitils.reflectionassert.ReflectionComparatorMode...)}
 * compares fields recursively via reflection, but requires instance types are
 * assignable.</li>
 * <li>{@link com.shazam.shazamcrest.matcher.Matchers<T>#sameBeanAs(T)} uses
 * Gson to convert instances to JSON then compare the result with jsonassert.
 * This is the approach used in this class.</li>
 * </ul>
 */
public class AssertModels {
	protected static GsonBuilder newGsonBuilder() {
		return new GsonBuilder().serializeNulls()
				.registerTypeAdapter(Date.class, new DateTimeTypeAdapter())
				.registerTypeAdapter(DateTime.class, new DateTimeTypeAdapter())
				.registerTypeAdapter(LocalDate.class,
						new LocalDateTypeAdapter());
	}

	protected static final Gson defaultGson = newGsonBuilder().create();
	protected static final JSONComparator agreesComparator = new CommonPropertiesJSONComparator(
			JSONCompareMode.STRICT);
	protected static final JSONComparator equalsComparator = new DefaultComparator(
			JSONCompareMode.STRICT);

	/**
	 * Asserts that two model class instances are equal for common properties.
	 */
	public static void assertModelAgrees(Object actual, Object expected) {
		assertModelAgreesExcluding(actual, expected,
				(Collection<ExclusionStrategy>) null);
	}

	/**
	 * Asserts that two model class instances are equal for common properties,
	 * excluding some fields.
	 */
	public static void assertModelAgreesExcluding(Object actual,
			Object expected, ExclusionStrategy exclusion) {
		assertModelAgreesExcluding(actual, expected,
				Collections.singleton(exclusion));
	}

	/**
	 * Asserts that two model class instances are equal for common properties,
	 * excluding some fields.
	 */
	public static void assertModelAgreesExcluding(Object actual,
			Object expected, Collection<ExclusionStrategy> exclusions) {
		assertModelComparesExcluding(actual, expected, exclusions,
				agreesComparator);
	}

	/**
	 * Asserts that two model class instances are equal for a given mode of
	 * comparison, excluding properties with given names.
	 */
	protected static void assertModelComparesExcluding(Object actual,
			Object expected, Collection<ExclusionStrategy> exclusions,
			JSONComparator comparator) {
		if (actual == null || expected == null) {
			Assert.assertEquals(actual, expected);
			return;
		}

		Gson gson;
		if (exclusions != null && !exclusions.isEmpty()) {
			GsonBuilder gsonBuilder = newGsonBuilder();
			for (ExclusionStrategy exclusion : exclusions) {
				gsonBuilder.addSerializationExclusionStrategy(exclusion);
			}
			gson = gsonBuilder.create();
		} else {
			gson = defaultGson;
		}

		JsonOrgTreeWriter actualWriter = new JsonOrgTreeWriter();
		gson.toJson(actual, actual.getClass(), actualWriter);
		Object actualElement = actualWriter.get();

		JsonOrgTreeWriter expectedWriter = new JsonOrgTreeWriter();
		gson.toJson(expected, expected.getClass(), expectedWriter);
		Object expectedElement = expectedWriter.get();

		try {
			if (actualElement instanceof JSONObject
					&& expectedElement instanceof JSONObject) {
				JSONObject actualObject = (JSONObject) actualElement;
				JSONObject expectedObject = (JSONObject) expectedElement;
				JSONCompareResult result = comparator
						.compareJSON(expectedObject, actualObject);
				if (result.failed()) {
					// Create TestNG failure so actual/expected can be compared
					// in tools such as the TestNG Eclipse Plugin
					String actualJson = actualObject.toString(2);
					String expectedJson = expectedObject.toString(2);
					Assert.assertSame(actualJson, expectedJson,
							result.getMessage());
				}
				return;
			}

			if (actualElement instanceof JSONArray
					&& expectedElement instanceof JSONArray) {
				JSONArray actualArray = (JSONArray) actualElement;
				JSONArray expectedArray = (JSONArray) expectedElement;
				JSONCompareResult result = comparator.compareJSON(expectedArray,
						actualArray);
				if (result.failed()) {
					// Create TestNG failure so actual/expected can be compared
					// in tools such as the TestNG Eclipse Plugin
					String actualJson = actualArray.toString(2);
					String expectedJson = expectedArray.toString(2);
					Assert.assertSame(actualJson, expectedJson,
							result.getMessage());
				}
				return;
			}
		} catch (JSONException e) {
			throw new AssertionError(e.getMessage(), e);
		}

		Assert.assertEquals(actual, expected);
	}

	/** Asserts that two model class instances are strictly equal. */
	public static void assertModelEquals(Object actual, Object expected) {
		assertModelEqualsExcluding(actual, expected,
				(Collection<ExclusionStrategy>) null);
	}

	/**
	 * Asserts that two model class instances are equal for a given mode of
	 * comparison, excluding some fields.
	 */
	public static void assertModelEqualsExcluding(Object actual,
			Object expected, ExclusionStrategy exclusion) {
		assertModelEqualsExcluding(actual, expected,
				Collections.singleton(exclusion));
	}

	/**
	 * Asserts that two model class instances are equal for a given mode of
	 * comparison, excluding properties with given names.
	 */
	public static void assertModelEqualsExcluding(Object actual,
			Object expected, Collection<ExclusionStrategy> exclusions) {
		assertModelComparesExcluding(actual, expected, exclusions,
				equalsComparator);
	}

	// Prevent instantiation
	private AssertModels() {
	}
}
