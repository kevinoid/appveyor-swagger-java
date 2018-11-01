package name.kevinlocke.appveyor;

import java.util.List;
import java.util.Map;

/**
 * An {@link name.kevinlocke.appveyor.ApiException} with a response body
 * model.
 */
public class ApiExceptionWithModel extends ApiException {
	private static final long serialVersionUID = 1L;

	private final name.kevinlocke.appveyor.model.Error responseModel;

	public ApiExceptionWithModel(String message, int code, Map<String, List<String>> responseHeaders,
			String responseBody, name.kevinlocke.appveyor.model.Error responseModel) {
		super(message, code, responseHeaders, responseBody);
		this.responseModel = responseModel;
	}

	/**
     * Get the deserialized HTTP response body.
     *
     * @return Deserialized response body, or {@code null}.
     */
    public name.kevinlocke.appveyor.model.Error getResponseModel() {
        return responseModel;
    }
}
