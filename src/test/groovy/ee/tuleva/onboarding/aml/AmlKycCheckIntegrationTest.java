package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.KYC_CHECK;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.*;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.IDENTITY_ONLY;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import java.util.Map;
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
  void onKycCheckPerformed_noneRisk_createsSuccessfulCheck() {
    var event =
        new KycCheckPerformedEvent(
            this,
            PERSONAL_CODE,
            new KycCheck(NONE, Map.of("riskLevel", "NONE")),
            PERSONAL_ONBOARDING);

    eventPublisher.publishEvent(event);

    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PERSONAL_CODE, aYearAgo());
    assertThat(checks)
        .singleElement()
        .satisfies(
            check -> {
              assertThat(check.getType()).isEqualTo(KYC_CHECK);
              assertThat(check.isSuccess()).isTrue();
              assertThat(check.getMetadata()).isEqualTo(Map.of("riskLevel", "NONE"));
            });
  }

  @Test
  void onKycCheckPerformed_lowRisk_createsSuccessfulCheck() {
    var event =
        new KycCheckPerformedEvent(
            this,
            PERSONAL_CODE,
            new KycCheck(LOW, Map.of("score", 10, "riskLevel", "LOW")),
            PERSONAL_ONBOARDING);

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
  void onKycCheckPerformed_mediumRisk_createsFailedCheck() {
    var event =
        new KycCheckPerformedEvent(
            this,
            PERSONAL_CODE,
            new KycCheck(MEDIUM, Map.of("score", 50, "riskLevel", "MEDIUM")),
            PERSONAL_ONBOARDING);

    eventPublisher.publishEvent(event);

    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PERSONAL_CODE, aYearAgo());
    assertThat(checks)
        .singleElement()
        .satisfies(
            check -> {
              assertThat(check.getType()).isEqualTo(KYC_CHECK);
              assertThat(check.isSuccess()).isFalse();
              assertThat(check.getMetadata()).isEqualTo(Map.of("score", 50, "riskLevel", "MEDIUM"));
            });
  }

  @Test
  void onKycCheckPerformed_identityOnlyPurpose_createsCheckIdentically() {
    var event =
        new KycCheckPerformedEvent(
            this, PERSONAL_CODE, new KycCheck(LOW, Map.of("riskLevel", "LOW")), IDENTITY_ONLY);

    eventPublisher.publishEvent(event);

    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PERSONAL_CODE, aYearAgo());
    assertThat(checks)
        .singleElement()
        .satisfies(
            check -> {
              assertThat(check.getType()).isEqualTo(KYC_CHECK);
              assertThat(check.isSuccess()).isTrue();
            });
  }

  @Test
  void onKycCheckPerformed_afterRecentFailedCheck_persistsFreshPassingCheck() {
    amlCheckRepository.save(
        AmlCheck.builder().personalCode(PERSONAL_CODE).type(KYC_CHECK).success(false).build());

    eventPublisher.publishEvent(
        new KycCheckPerformedEvent(
            this,
            PERSONAL_CODE,
            new KycCheck(LOW, Map.of("riskLevel", "LOW")),
            PERSONAL_ONBOARDING));

    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PERSONAL_CODE, aYearAgo());
    assertThat(checks).hasSize(2);
    assertThat(checks)
        .filteredOn(AmlCheck::isSuccess)
        .singleElement()
        .satisfies(
            check -> {
              assertThat(check.getType()).isEqualTo(KYC_CHECK);
              assertThat(check.getMetadata()).isEqualTo(Map.of("riskLevel", "LOW"));
            });
  }

  @Test
  void onKycCheckPerformed_afterRecentFailedCheck_persistsStillFailingRecheck() {
    amlCheckRepository.save(
        AmlCheck.builder().personalCode(PERSONAL_CODE).type(KYC_CHECK).success(false).build());

    eventPublisher.publishEvent(
        new KycCheckPerformedEvent(
            this,
            PERSONAL_CODE,
            new KycCheck(HIGH, Map.of("score", 99, "riskLevel", "HIGH")),
            PERSONAL_ONBOARDING));

    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PERSONAL_CODE, aYearAgo());
    assertThat(checks).hasSize(2);
    assertThat(checks).allSatisfy(check -> assertThat(check.isSuccess()).isFalse());
  }

  @Test
  void onKycCheckPerformed_afterRecentSuccessfulCheck_dedupesPassingRecheck() {
    amlCheckRepository.save(
        AmlCheck.builder().personalCode(PERSONAL_CODE).type(KYC_CHECK).success(true).build());

    eventPublisher.publishEvent(
        new KycCheckPerformedEvent(
            this,
            PERSONAL_CODE,
            new KycCheck(LOW, Map.of("riskLevel", "LOW")),
            PERSONAL_ONBOARDING));

    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PERSONAL_CODE, aYearAgo());
    assertThat(checks).hasSize(1);
  }

  @Test
  void onKycCheckPerformed_afterRecentSuccessfulCheck_persistsAdverseRecheck() {
    amlCheckRepository.save(
        AmlCheck.builder().personalCode(PERSONAL_CODE).type(KYC_CHECK).success(true).build());

    eventPublisher.publishEvent(
        new KycCheckPerformedEvent(
            this,
            PERSONAL_CODE,
            new KycCheck(HIGH, Map.of("score", 99, "riskLevel", "HIGH")),
            PERSONAL_ONBOARDING));

    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PERSONAL_CODE, aYearAgo());
    assertThat(checks).hasSize(2);
    assertThat(checks)
        .filteredOn(check -> !check.isSuccess())
        .singleElement()
        .satisfies(
            check -> {
              assertThat(check.getType()).isEqualTo(KYC_CHECK);
              assertThat(check.getMetadata()).isEqualTo(Map.of("score", 99, "riskLevel", "HIGH"));
            });
  }

  @Test
  void onKycCheckPerformed_highRisk_createsFailedCheck() {
    var event =
        new KycCheckPerformedEvent(
            this,
            PERSONAL_CODE,
            new KycCheck(HIGH, Map.of("score", 99, "riskLevel", "HIGH")),
            PERSONAL_ONBOARDING);

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
