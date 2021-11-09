package ee.tuleva.onboarding.user.exception;

import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class DuplicateEmailException extends ErrorsResponseException {

  public DuplicateEmailException(ErrorsResponse errorsResponse) {
    super(errorsResponse);
  }

  public static DuplicateEmailException newInstance() {
    return new DuplicateEmailException(
        ErrorsResponse.ofSingleError(
            "new.user.flow.signup.error.email.duplicate", "This e-mail is already in use"));
  }
}
