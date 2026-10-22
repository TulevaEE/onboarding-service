package ee.tuleva.onboarding.insurance;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.time.LocalDate;

class IdentityDocumentExpiredException extends ErrorsResponseException {

  IdentityDocumentExpiredException(LocalDate validUntil, LocalDate today) {
    super(
        ErrorsResponse.ofSingleError(
            "insurance.transfer.form.id.document.expired",
            "Identity document has expired: validUntil=" + validUntil + ", today=" + today));
  }
}
