package ee.tuleva.onboarding.auth.response;

import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.io.Serial;

public class AuthNotCompleteException extends ErrorsResponseException {
  @Serial private static final long serialVersionUID = -7114237655758069952L;

  public AuthNotCompleteException() {
    super(ErrorsResponse.ofSingleError("authentication.not.complete", "Please keep polling."));
  }
}
