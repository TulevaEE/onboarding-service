package ee.tuleva.onboarding.signature;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class SignatureStateException extends ErrorsResponseException {

  private SignatureStateException(String code, String message) {
    super(ErrorsResponse.ofSingleError(code, message));
  }

  public static SignatureStateException alreadySigned(String entity, Object entityId) {
    return new SignatureStateException(
        "signature.already.signed", entity + " is already signed: id=" + entityId);
  }

  public static SignatureStateException notSigned(String entity, Object entityId) {
    return new SignatureStateException(
        "signature.not.signed", entity + " is not signed: id=" + entityId);
  }
}
