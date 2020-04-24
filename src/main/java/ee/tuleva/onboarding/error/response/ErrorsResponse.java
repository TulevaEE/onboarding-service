package ee.tuleva.onboarding.error.response;

import lombok.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ErrorsResponse {

	private List<ErrorResponse> errors = new ArrayList<>();

	public static ErrorsResponse ofSingleError(String code, String message) {
		return new ErrorsResponse(Collections.singletonList(ErrorResponse.builder().code(code).message(message).build()));
	}

	public static ErrorsResponse ofSingleError(ErrorResponse error) {
		return new ErrorsResponse(Collections.singletonList(error));
	}

	public boolean hasErrors() {
		return errors.size() > 0;
	}

    public void add(ErrorResponse error) {
        errors.add(error);
    }
}
