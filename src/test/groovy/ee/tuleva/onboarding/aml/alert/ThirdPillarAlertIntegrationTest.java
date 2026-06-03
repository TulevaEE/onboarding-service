package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.aml.alert.AmlAlertType.III_PILLAR_DEPOSIT_PERSON;
import static ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionFixture.exampleTransactionBuilder;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransaction;
import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ThirdPillarAlertIntegrationTest {

  @Autowired private ThirdPillarAlertService thirdPillarAlertService;
  @Autowired private AnalyticsThirdPillarTransactionRepository transactionRepository;
  @Autowired private AmlThirdPillarAlertRepository alertRepository;

  @MockitoBean private OperationsNotificationService notificationService;

  private AnalyticsThirdPillarTransaction qualifyingDeposit() {
    return exampleTransactionBuilder()
        .reportingDate(LocalDate.now())
        .personalId("38001010000")
        .accountNo("EE100200300")
        .transactionSource("Osakute väljalase isikult laekumiste alusel")
        .transactionType("irrelevant")
        .transactionValue(new BigDecimal("6001.00"))
        .build();
  }

  @Test
  @DisplayName(
      "alerts a qualifying III pillar transaction exactly once, is idempotent, and survives a re-sync that regenerates the row id")
  void endToEnd_qualifyingTransaction_alertsOnceAndSurvivesResync() {
    AnalyticsThirdPillarTransaction qualifying = transactionRepository.save(qualifyingDeposit());

    AnalyticsThirdPillarTransaction nonQualifying =
        transactionRepository.save(
            exampleTransactionBuilder()
                .reportingDate(LocalDate.now())
                .personalId("38001010000")
                .transactionSource("Osakute väljalase isikult laekumiste alusel")
                .transactionType("irrelevant")
                .transactionValue(new BigDecimal("100.00"))
                .build());

    thirdPillarAlertService.checkAndAlert();

    verify(notificationService, times(1))
        .sendMessage(
            argThat(message -> message.startsWith("AML alert: III_PILLAR_DEPOSIT_PERSON")),
            eq(AML));
    verify(notificationService, never())
        .sendMessage(argThat(message -> message.contains("ref=" + nonQualifying.getId())), eq(AML));

    assertThat(
            alertRepository.existsByTransactionFingerprintAndAlertType(
                ThirdPillarTransactionFingerprint.of(qualifying), III_PILLAR_DEPOSIT_PERSON))
        .isTrue();

    // Idempotent on an unchanged re-run.
    thirdPillarAlertService.checkAndAlert();
    verify(notificationService, times(1))
        .sendMessage(
            argThat(message -> message.startsWith("AML alert: III_PILLAR_DEPOSIT_PERSON")),
            eq(AML));

    // Re-sync: the analytics sync deletes and re-inserts the reporting-date range, so the same
    // EPIS transaction comes back with a NEW auto-generated id. Dedup must survive that.
    transactionRepository.delete(qualifying);
    AnalyticsThirdPillarTransaction resynced = transactionRepository.save(qualifyingDeposit());
    assertThat(resynced.getId()).isNotEqualTo(qualifying.getId());

    thirdPillarAlertService.checkAndAlert();

    // Still exactly one alert overall — the regenerated id did NOT cause a duplicate.
    verify(notificationService, times(1))
        .sendMessage(
            argThat(message -> message.startsWith("AML alert: III_PILLAR_DEPOSIT_PERSON")),
            eq(AML));
  }
}
