package ee.tuleva.onboarding.mandate.exception;

import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class IdSessionException extends ErrorsResponseException {

  public IdSessionException(ErrorsResponse errorsResponse) {
    super(errorsResponse);
  }

  public static IdSessionException mobileSessionNotFound() {
    return new IdSessionException(
        ErrorsResponse.ofSingleError("mobile.id.session.not.found", "Mobile id session not found"));
  }

  public static IdSessionException smartIdSessionNotFound() {
    return new IdSessionException(
        ErrorsResponse.ofSingleError("smart.id.session.not.found", "Smart ID session not found"));
  }

  public static IdSessionException mobileSignatureSessionNotFound() {
    return new IdSessionException(
        ErrorsResponse.ofSingleError(
            "mobile.id.signature.session.not.found", "Mobile id signature session not found"));
  }

  public static IdSessionException smartIdSignatureSessionNotFound() {
    return new IdSessionException(
        ErrorsResponse.ofSingleError(
            "smart.id.signature.session.not.found", "Smart ID signature session not found"));
  }

  public static IdSessionException cardSignatureSessionNotFound() {
    return new IdSessionException(
        ErrorsResponse.ofSingleError(
            "id.card.signature.session.not.found", "No ID card signature session found"));
  }
}
