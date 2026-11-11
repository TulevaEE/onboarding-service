package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.user.User;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DevKycCheckerTest {

  private final User user = User.builder().id(7L).personalCode("38888888888").build();

  @Test
  void passesEveryoneAsLowRiskSayingNothingWasAssessed() {
    assertThat(new DevKycChecker().check(user))
        .isEqualTo(
            new KycCheck(
                LOW,
                Map.of("assessedBy", "the aml-service mock in dev and staging, nothing assessed")));
  }
}
