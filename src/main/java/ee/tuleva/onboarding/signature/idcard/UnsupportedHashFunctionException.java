package ee.tuleva.onboarding.signature.idcard;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.util.List;

public class UnsupportedHashFunctionException extends ErrorsResponseException {

  public UnsupportedHashFunctionException(String hashFunction, List<String> supported) {
    super(
        ErrorsResponse.ofSingleError(
            "id.card.signing.hash.function.unsupported",
            "Signing certificate requires a hash function the card does not support: required="
                + hashFunction
                + ", supported="
                + supported));
  }
}
