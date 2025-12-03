package ee.tuleva.onboarding.kyc;

import java.util.Objects;

public record KycCheckPerformedEvent(String personalCode, KycCheck kycCheck) {
  public KycCheckPerformedEvent {
    Objects.requireNonNull(personalCode);
    Objects.requireNonNull(kycCheck);
  }
}
