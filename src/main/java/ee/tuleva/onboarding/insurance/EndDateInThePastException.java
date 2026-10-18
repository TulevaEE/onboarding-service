package ee.tuleva.onboarding.insurance;

import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.time.LocalDate;

class EndDateInThePastException extends ErrorsResponseException {

  EndDateInThePastException(LocalDate endDate, LocalDate today) {
    super(
        ErrorsResponse.ofSingleError(
            "insurance.transfer.form.end.date.passed",
            "End date is in the past: endDate=" + endDate + ", today=" + today));
  }
}
