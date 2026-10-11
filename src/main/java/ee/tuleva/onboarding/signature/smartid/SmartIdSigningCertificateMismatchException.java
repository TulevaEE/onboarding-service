package ee.tuleva.onboarding.signature.smartid;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class SmartIdSigningCertificateMismatchException extends ErrorsResponseException {

  public SmartIdSigningCertificateMismatchException() {
    super(
        ErrorsResponse.ofSingleError(
            "smart.id.signing.certificate.mismatch",
            "Smart-ID signing certificate belongs to someone other than the signer"));
  }
}
