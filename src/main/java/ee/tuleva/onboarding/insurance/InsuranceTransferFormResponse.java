package ee.tuleva.onboarding.insurance;

import org.jspecify.annotations.Nullable;

public record InsuranceTransferFormResponse(
    Long id,
    Insurer insurer,
    String insurerEmail,
    InsuranceTransferFormStatus status,
    @Nullable InsuranceTransferEmailStatus emailStatus) {

  static InsuranceTransferFormResponse from(InsuranceTransferForm form) {
    return new InsuranceTransferFormResponse(
        form.getIdOrThrow(),
        form.getInsurer(),
        form.getInsurer().getEmail(),
        form.getStatus(),
        form.getEmailStatus());
  }
}
