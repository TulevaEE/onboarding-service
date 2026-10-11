package ee.tuleva.onboarding.signature.idcard;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class InvalidSignatureException extends ErrorsResponseException {

  public InvalidSignatureException() {
    super(
        ErrorsResponse.ofSingleError(
            "id.card.signature.invalid",
            "Signature is not a base64 signature over the hash to sign"));
  }

  public InvalidSignatureException(Exception cause) {
    this();
    initCause(cause);
  }
}
