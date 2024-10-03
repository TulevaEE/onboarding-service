package ee.tuleva.onboarding.mandate.exception;

import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class MandateProcessingException extends ErrorsResponseException {

  public MandateProcessingException(ErrorsResponse errorsResponse) {
    super(errorsResponse);
  }
}
