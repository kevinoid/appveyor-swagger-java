package name.kevinlocke.appveyor.testutils.json;

import static org.skyscreamer.jsonassert.comparator.JSONCompareUtil.qualify;

import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;
import org.skyscreamer.jsonassert.comparator.DefaultComparator;

public class CommonPropertiesJSONComparator extends DefaultComparator {
	public CommonPropertiesJSONComparator(JSONCompareMode mode) {
		super(mode);
	}

	@Override
	public void compareJSON(String prefix, JSONObject expected,
			JSONObject actual, JSONCompareResult result) throws JSONException {
		Iterator<?> expectedKeyIter = expected.keys();
		while (expectedKeyIter.hasNext()) {
			String key = (String) expectedKeyIter.next();
			Object actualValue = actual.opt(key);
			if (actualValue != null) {
				Object expectedValue = expected.get(key);
				compareValues(qualify(prefix, key), expectedValue, actualValue,
						result);
			}
		}
	}
}