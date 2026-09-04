package ee.tuleva.onboarding.signature.idcard;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class SigningCertificateMismatchException extends ErrorsResponseException {

  public SigningCertificateMismatchException() {
    super(
        ErrorsResponse.ofSingleError(
            "id.card.signing.certificate.mismatch",
            "Signing certificate belongs to someone other than the signer"));
  }
}
