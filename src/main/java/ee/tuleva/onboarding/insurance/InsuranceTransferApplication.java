package ee.tuleva.onboarding.insurance;

import static java.util.Objects.requireNonNullElse;

import java.time.LocalDate;

record InsuranceTransferApplication(
    Applicant applicant, CreateInsuranceTransferFormCommand request, LocalDate today) {

  Insurer insurer() {
    return request.insurer();
  }

  LocalDate endDateOrToday() {
    return requireNonNullElse(request.endDate(), today);
  }
}
