package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.KYC_CHECK;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.HIGH;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AmlKycCheckIntegrationTest {

  private static final String PERSONAL_CODE = "38501010002";

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private AmlCheckRepository amlCheckRepository;

  @Test
  @DisplayName("KycCheckPerformedEvent with LOW risk creates successful AmlCheck")
  void onKycCheckPerformed_lowRisk_createsSuccessfulCheck() {
    var event = new KycCheckPerformedEvent(PERSONAL_CODE, new KycCheck(10, LOW));

    eventPublisher.publishEvent(event);

    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PERSONAL_CODE, aYearAgo());
    assertThat(checks)
        .singleElement()
        .satisfies(
            check -> {
              assertThat(check.getType()).isEqualTo(KYC_CHECK);
              assertThat(check.isSuccess()).isTrue();
              assertThat(check.getMetadata()).isEqualTo(Map.of("score", 10, "riskLevel", "LOW"));
            });
  }

  @Test
  @DisplayName("KycCheckPerformedEvent with HIGH risk creates failed AmlCheck")
  void onKycCheckPerformed_highRisk_createsFailedCheck() {
    var event = new KycCheckPerformedEvent(PERSONAL_CODE, new KycCheck(99, HIGH));

    eventPublisher.publishEvent(event);

    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PERSONAL_CODE, aYearAgo());
    assertThat(checks)
        .singleElement()
        .satisfies(
            check -> {
              assertThat(check.getType()).isEqualTo(KYC_CHECK);
              assertThat(check.isSuccess()).isFalse();
              assertThat(check.getMetadata()).isEqualTo(Map.of("score", 99, "riskLevel", "HIGH"));
            });
  }
}
