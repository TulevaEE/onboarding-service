package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.InsuranceTransferFormStatus.CREATED;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormStatus.SIGNED;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

record InsuranceTransferFormSummary(
    Long id,
    Insurer insurer,
    @Nullable Instant signedAt,
    @Nullable InsuranceTransferEmailStatus emailStatus) {

  boolean isSigned() {
    return signedAt != null;
  }

  InsuranceTransferFormStatus status() {
    return isSigned() ? SIGNED : CREATED;
  }
}
