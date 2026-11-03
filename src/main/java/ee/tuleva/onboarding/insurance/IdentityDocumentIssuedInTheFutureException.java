package ee.tuleva.onboarding.insurance;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.time.LocalDate;

class IdentityDocumentIssuedInTheFutureException extends ErrorsResponseException {

  IdentityDocumentIssuedInTheFutureException(LocalDate issuedOn, LocalDate today) {
    super(
        ErrorsResponse.ofSingleError(
            "insurance.transfer.form.id.document.issued.in.future",
            "Identity document issue date is in the future: issuedOn="
                + issuedOn
                + ", today="
                + today));
  }
}
