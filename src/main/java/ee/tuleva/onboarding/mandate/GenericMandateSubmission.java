package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.mandate.details.MandateDetails;
import java.time.Instant;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record GenericMandateSubmission<T extends MandateDetails>(
    @Nullable Long id,
    T details,
    @Nullable Instant createdDate,
    @Nullable Country address,
    @Nullable String email,
    @Nullable String phoneNumber) {

  public MandateType getMandateType() {
    return details.getMandateType();
  }
}
