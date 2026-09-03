package ee.tuleva.onboarding.signature.idcard;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class InvalidSigningCertificateException extends ErrorsResponseException {

  public InvalidSigningCertificateException(Exception cause) {
    super(
        ErrorsResponse.ofSingleError(
            "id.card.signing.certificate.invalid",
            "Signing certificate is not a base64 encoded DER certificate"));
    initCause(cause);
  }
}
