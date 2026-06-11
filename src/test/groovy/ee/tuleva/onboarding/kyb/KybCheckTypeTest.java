package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KybCheckTypeTest {

  @Test
  void riskSignalChecksDoNotGateOnboarding() {
    assertThat(COMPANY_AGE.isOnboardingGate()).isFalse();
    assertThat(OWNER_CHANGED.isOnboardingGate()).isFalse();
    assertThat(DATA_CHANGED.isOnboardingGate()).isFalse();
  }

  @Test
  void hardRequirementChecksGateOnboarding() {
    assertThat(COMPANY_ACTIVE.isOnboardingGate()).isTrue();
    assertThat(COMPANY_STRUCTURE.isOnboardingGate()).isTrue();
    assertThat(COMPANY_SANCTION.isOnboardingGate()).isTrue();
    assertThat(SELF_CERTIFICATION.isOnboardingGate()).isTrue();
    assertThat(RELATED_PERSONS_KYC.isOnboardingGate()).isTrue();
  }
}
