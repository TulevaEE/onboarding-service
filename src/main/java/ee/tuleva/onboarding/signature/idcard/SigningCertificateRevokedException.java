package ee.tuleva.onboarding.signature.idcard;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class SigningCertificateRevokedException extends ErrorsResponseException {

  public SigningCertificateRevokedException(Exception cause) {
    super(
        ErrorsResponse.ofSingleError(
            "id.card.signing.certificate.revoked", "Signing certificate is revoked or suspended"));
    initCause(cause);
  }
}
