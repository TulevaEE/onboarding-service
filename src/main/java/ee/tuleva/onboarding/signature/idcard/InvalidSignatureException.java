package ee.tuleva.onboarding.signature.idcard;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class InvalidSignatureException extends ErrorsResponseException {

  public InvalidSignatureException(Exception cause) {
    super(
        ErrorsResponse.ofSingleError(
            "id.card.signature.invalid", "Signature is not base64 encoded"));
    initCause(cause);
  }
}
