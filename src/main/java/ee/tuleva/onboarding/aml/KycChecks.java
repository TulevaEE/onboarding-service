package ee.tuleva.onboarding.aml;

import java.util.Optional;

public interface KycChecks {

  Optional<Boolean> latestKycCheckPassedWithinLastYear(String personalCode);
}
