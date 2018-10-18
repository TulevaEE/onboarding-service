package ee.tuleva.onboarding.error.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@Getter
@NoArgsConstructor
@ToString
public class ErrorsResponse {

	private List<ErrorResponse> errors;

	public static ErrorsResponse ofSingleError(String code, String message) {
		return new ErrorsResponse(Collections.singletonList(ErrorResponse.builder().code(code).message(message).build()));
	}

	public static ErrorsResponse ofSingleError(ErrorResponse error) {
		return new ErrorsResponse(Collections.singletonList(error));
	}

	public ErrorsResponse(List<ErrorResponse> errors) {
		this.errors = errors;
	}

	public boolean hasErrors() {
		return errors.size() > 0;
	}
}
