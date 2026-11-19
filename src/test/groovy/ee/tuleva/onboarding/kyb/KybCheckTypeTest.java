package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class KybCheckTypeTest {

  @Test
  void riskSignalChecksDoNotGateOnboarding() {
    assertThat(COMPANY_AGE.isOnboardingGate()).isFalse();
    assertThat(DATA_CHANGED.isOnboardingGate()).isFalse();
    assertThat(COMPANY_PARTIES.isOnboardingGate()).isFalse();
    assertThat(OWNERSHIP_HISTORY.isOnboardingGate()).isFalse();
    assertThat(ANNUAL_REPORTS.isOnboardingGate()).isFalse();
  }

  @Test
  void hardRequirementChecksGateOnboarding() {
    assertThat(COMPANY_ACTIVE.isOnboardingGate()).isTrue();
    assertThat(COMPANY_STRUCTURE.isOnboardingGate()).isTrue();
    assertThat(COMPANY_SANCTION.isOnboardingGate()).isTrue();
    assertThat(SELF_CERTIFICATION.isOnboardingGate()).isTrue();
    assertThat(RELATED_PERSONS_KYC.isOnboardingGate()).isTrue();
  }

  @Test
  void anAmlOfficerCanApproveAReviewedActivityOrOwnershipFailure() {
    assertThat(HIGH_RISK_NACE.isManuallyForceable()).isTrue();
    assertThat(SINGLE_BOARD_MEMBER_OWNERSHIP.isManuallyForceable()).isTrue();
    assertThat(COMPANY_SANCTION.isManuallyForceable()).isFalse();
    assertThat(COMPANY_PARTIES.isManuallyForceable()).isFalse();
    assertThat(OWNERSHIP_HISTORY.isManuallyForceable()).isFalse();
    assertThat(ANNUAL_REPORTS.isManuallyForceable()).isFalse();
  }

  @Test
  void aHighRiskIndicatorHoldsAnOnboardingForReviewWhereAnEligibilityFailureRejectsIt() {
    assertThat(
            Arrays.stream(KybCheckType.values()).filter(KybCheckType::isHighRiskIndicator).toList())
        .containsExactly(
            SOLE_MEMBER_OWNERSHIP,
            DUAL_MEMBER_OWNERSHIP,
            SINGLE_BOARD_MEMBER_OWNERSHIP,
            COMPANY_ACTIVE,
            COMPANY_SANCTION,
            COMPANY_PEP,
            HIGH_RISK_NACE);
  }
}
