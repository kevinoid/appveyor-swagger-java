package name.kevinlocke.appveyor.testutils.json;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

public class FieldNameExclusionStrategy implements ExclusionStrategy {
	protected final Collection<String> fieldNames;

	public FieldNameExclusionStrategy(String fieldName) {
		fieldNames = Collections.singleton(fieldName);
	}

	public FieldNameExclusionStrategy(String... fieldNames) {
		this(Arrays.asList(fieldNames));
	}

	public FieldNameExclusionStrategy(Collection<String> fieldNames) {
		this.fieldNames = fieldNames;
	}

	public Collection<String> getFieldNames() {
		return fieldNames;
	}

	@Override
	public boolean shouldSkipField(FieldAttributes f) {
		return fieldNames.contains(f.getName());
	}

	@Override
	public boolean shouldSkipClass(Class<?> clazz) {
		return false;
	}
}