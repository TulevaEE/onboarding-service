package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;

public class PensionRegistryAccountStatementConnectionException extends ErrorsResponseException {

  public PensionRegistryAccountStatementConnectionException() {
    super(
        ErrorsResponse.ofSingleError(
            "pension.registry.connection.exception",
            "Couldn't get account statement from pension registry."));
  }
}
