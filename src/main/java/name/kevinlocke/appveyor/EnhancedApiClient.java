package name.kevinlocke.appveyor;

import java.io.IOException;
import java.lang.reflect.Type;

import com.google.gson.JsonParseException;
import com.squareup.okhttp.Response;

/**
 * AppVeyor API Client with enhancements not present in the generated
 * {@link name.kevinlocke.appveyor.ApiClient}.
 *
 * Support deserializing {@link name.kevinlocke.appveyor.model.Error} from the
 * body of HTTP errors and throwing it inside an
 * {@link name.kevinlocke.appveyor.ApiExceptionWithModel}.
 * https://github.com/swagger-api/swagger-codegen/issues/2602
 */
public class EnhancedApiClient extends ApiClient {
	@Override
	public <T> T handleResponse(Response response, Type returnType) throws ApiException {
		if (!response.isSuccessful()) {
			String bodyString;
			try {
				bodyString = response.body().string();
			} catch (IOException ioException) {
				throw new ApiException(response.message(), ioException,
						response.code(), response.headers().toMultimap());
			}

			name.kevinlocke.appveyor.model.Error responseModel;
			try {
				// Note: Error responses have type text/plain
				//       (when the URL path doesn't have an extension)
				//       which would cause this.deserialize() to fail.
				//       It would also require extra code to buffer the body.
				//       Instead, try to parse the string directly.
				responseModel = this.getJSON()
						.deserialize(bodyString, Error.class.getClass());
			} catch (JsonParseException parseException) {
				throw new ApiException(response.message(), parseException,
						response.code(), response.headers().toMultimap(), bodyString);
			}

			throw new ApiExceptionWithModel(response.message(), response.code(),
					response.headers().toMultimap(), bodyString, responseModel);
		}

		return super.handleResponse(response, returnType);
	}
}
