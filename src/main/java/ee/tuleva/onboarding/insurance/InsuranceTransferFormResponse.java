package ee.tuleva.onboarding.insurance;

import org.jspecify.annotations.Nullable;

public record InsuranceTransferFormResponse(
    Long id,
    Insurer insurer,
    String insurerEmail,
    InsuranceTransferFormStatus status,
    @Nullable InsuranceTransferEmailStatus emailStatus) {

  static InsuranceTransferFormResponse from(InsuranceTransferFormSummary form) {
    return new InsuranceTransferFormResponse(
        form.id(), form.insurer(), form.insurer().getEmail(), form.status(), form.emailStatus());
  }
}
