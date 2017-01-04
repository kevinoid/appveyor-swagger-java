package name.kevinlocke.appveyor.testutils;

/** Exception representing a failed assertion comparing two values. */
public class ComparisonFailure extends AssertionError {
	private static final long serialVersionUID = 8862641219602274345L;

	protected static String buildMessage(String explanation, Object expected,
			String comparison, Object actual) {
		String message;
		if (explanation != null && explanation.length() > 0) {
			message = explanation + ": ";
		} else {
			message = "";
		}
		message += String.valueOf(expected) + " " + comparison + " "
				+ String.valueOf(actual);
		return message;
	}

	protected final Object expected;
	protected final String comparison;
	protected final Object actual;

	/**
	 * Creates a new ComparisonFailure.
	 *
	 * @param explanation Optional message explaining the failure.
	 * @param expected Expected value compared against.
	 * @param comparison How {@code expected} should compare to {@code actual}.
	 * Usually an operator. (e.g. {@code "=="})
	 * @param actual Actual value.
	 */
	public ComparisonFailure(String explanation, Object expected,
			String comparison, Object actual) {
		super(buildMessage(explanation, expected, comparison, actual));
		this.expected = expected;
		this.comparison = comparison;
		this.actual = actual;
	}

	/** Gets the expected value compared against. */
	public Object getExpected() {
		return expected;
	}

	/** Gets the comparison performed. */
	public String getComparison() {
		return comparison;
	}

	/** Gets the actual value. */
	public Object getActual() {
		return actual;
	}
}
