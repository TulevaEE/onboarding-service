package ee.tuleva.onboarding.error.response;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
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

	public String toString() {
		return errors.stream()
				.map(errorResponse -> errorResponse.getCode()
						.concat(";")).reduce(String::concat)
				.orElse("No errors present");
	}
}
