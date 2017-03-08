/* Based on com.google.gson.internal.bind.JsonTreeWriter
 * https://github.com/google/gson/blob/gson-parent-2.8.0/gson/src/main/java/com/google/gson/internal/bind/JsonTreeWriter.java
 * with the following copyright declaration:
 *
 * Copyright (C) 2011 Google Inc.
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
 */

package name.kevinlocke.appveyor.testutils.json;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * This writer creates a JSONArray, JSONObject, or primitive value.
 */
public final class JsonOrgTreeWriter extends JsonWriter {
	private static final Writer UNWRITABLE_WRITER = new Writer() {
		@Override
		public void write(char[] buffer, int offset, int counter) {
			throw new AssertionError();
		}

		@Override
		public void flush() throws IOException {
			throw new AssertionError();
		}

		@Override
		public void close() throws IOException {
			throw new AssertionError();
		}
	};
	/**
	 * Added to the top of the stack when this writer is closed to cause
	 * following ops to fail.
	 */
	private static final String SENTINEL_CLOSED = "closed";

	/**
	 * The JSONObjects and JSONArrays under modification, outermost to
	 * innermost.
	 */
	private final List<Object> stack = new ArrayList<Object>();

	/**
	 * The name for the next JSON object value. If non-null, the top of the
	 * stack is a JSONObject.
	 */
	private String pendingName;

	/** the JSON object constructed by this writer. */
	private Object product = JSONObject.NULL; // TODO: is this really
												// what we want?;

	public JsonOrgTreeWriter() {
		super(UNWRITABLE_WRITER);
	}

	/**
	 * Returns the top level object produced by this writer.
	 */
	public Object get() {
		if (!stack.isEmpty()) {
			throw new IllegalStateException(
					"Expected one JSON element but was " + stack);
		}
		return product;
	}

	private Object peek() {
		return stack.get(stack.size() - 1);
	}

	private void put(Object value) {
		if (pendingName != null) {
			if (value != JSONObject.NULL || getSerializeNulls()) {
				JSONObject object = (JSONObject) peek();
				try {
					object.put(pendingName, value);
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
			}
			pendingName = null;
		} else if (stack.isEmpty()) {
			product = value;
		} else {
			Object element = peek();
			if (element instanceof JSONArray) {
				((JSONArray) element).put(value);
			} else {
				throw new IllegalStateException();
			}
		}
	}

	@Override
	public JsonWriter beginArray() throws IOException {
		JSONArray array = new JSONArray();
		put(array);
		stack.add(array);
		return this;
	}

	@Override
	public JsonWriter endArray() throws IOException {
		if (stack.isEmpty() || pendingName != null) {
			throw new IllegalStateException();
		}
		Object element = peek();
		if (element instanceof JSONArray) {
			stack.remove(stack.size() - 1);
			return this;
		}
		throw new IllegalStateException();
	}

	@Override
	public JsonWriter beginObject() throws IOException {
		JSONObject object = new JSONObject();
		put(object);
		stack.add(object);
		return this;
	}

	@Override
	public JsonWriter endObject() throws IOException {
		if (stack.isEmpty() || pendingName != null) {
			throw new IllegalStateException();
		}
		Object element = peek();
		if (element instanceof JSONObject) {
			stack.remove(stack.size() - 1);
			return this;
		}
		throw new IllegalStateException();
	}

	@Override
	public JsonWriter name(String name) throws IOException {
		if (stack.isEmpty() || pendingName != null) {
			throw new IllegalStateException();
		}
		Object element = peek();
		if (element instanceof JSONObject) {
			pendingName = name;
			return this;
		}
		throw new IllegalStateException();
	}

	@Override
	public JsonWriter value(String value) throws IOException {
		if (value == null) {
			return nullValue();
		}
		put(value);
		return this;
	}

	@Override
	public JsonWriter nullValue() throws IOException {
		put(JSONObject.NULL);
		return this;
	}

	@Override
	public JsonWriter value(boolean value) throws IOException {
		put(value);
		return this;
	}

	@Override
	public JsonWriter value(Boolean value) throws IOException {
		if (value == null) {
			return nullValue();
		}
		put(value);
		return this;
	}

	@Override
	public JsonWriter value(double value) throws IOException {
		if (!isLenient() && (Double.isNaN(value) || Double.isInfinite(value))) {
			throw new IllegalArgumentException(
					"JSON forbids NaN and infinities: " + value);
		}
		put(value);
		return this;
	}

	@Override
	public JsonWriter value(long value) throws IOException {
		put(value);
		return this;
	}

	@Override
	public JsonWriter value(Number value) throws IOException {
		if (value == null) {
			return nullValue();
		}

		if (!isLenient()) {
			double d = value.doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				throw new IllegalArgumentException(
						"JSON forbids NaN and infinities: " + value);
			}
		}

		put(value);
		return this;
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
		if (!stack.isEmpty()) {
			throw new IOException("Incomplete document");
		}
		stack.add(SENTINEL_CLOSED);
	}
}
