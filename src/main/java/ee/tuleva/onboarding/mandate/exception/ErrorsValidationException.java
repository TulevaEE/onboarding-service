package ee.tuleva.onboarding.mandate.exception;

import lombok.Getter;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = org.springframework.http.HttpStatus.BAD_REQUEST)
public class ErrorsValidationException extends RuntimeException {
	private static final long serialVersionUID = -5913149288101773L;

	@Getter
	private Errors errors;

	public ErrorsValidationException(Errors errors) {
		this.errors = errors;
	}
}
