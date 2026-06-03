package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.aml.alert.AmlAlertType.III_PILLAR_DEPOSIT_PERSON;
import static ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionFixture.exampleTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransaction;
import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ThirdPillarAlertServiceTest {

  @Mock private AnalyticsThirdPillarTransactionRepository transactionRepository;
  @Mock private AmlThirdPillarAlertRepository alertRepository;
  @Mock private ThirdPillarAlertEvaluator evaluator;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-02T08:00:00Z"), ZoneOffset.UTC);

  private ThirdPillarAlertService service;

  @BeforeEach
  void setUp() {
    service =
        new ThirdPillarAlertService(
            transactionRepository, alertRepository, evaluator, eventPublisher, clock);
  }

  private AnalyticsThirdPillarTransaction qualifyingTransaction() {
    var transaction = exampleTransaction();
    transaction.setId(7L);
    transaction.setPersonalId("38001010000");
    transaction.setTransactionValue(new BigDecimal("6001.00"));
    return transaction;
  }

  @Test
  @DisplayName("reads transactions within the lookback window from the injected clock")
  void checkAndAlert_usesLookbackCutoff() {
    when(transactionRepository.findByReportingDateGreaterThanEqual(any())).thenReturn(List.of());

    service.checkAndAlert();

    verify(transactionRepository)
        .findByReportingDateGreaterThanEqual(LocalDate.of(2026, 6, 2).minusDays(40));
  }

  @Test
  @DisplayName("publishes an alert event and persists a tracking row for a qualifying transaction")
  void checkAndAlert_qualifying_publishesEventAndTracks() {
    var transaction = qualifyingTransaction();
    var fingerprint = ThirdPillarTransactionFingerprint.of(transaction);
    when(transactionRepository.findByReportingDateGreaterThanEqual(any()))
        .thenReturn(List.of(transaction));
    when(evaluator.evaluate(transaction)).thenReturn(List.of(III_PILLAR_DEPOSIT_PERSON));
    when(alertRepository.existsByTransactionFingerprintAndAlertType(
            fingerprint, III_PILLAR_DEPOSIT_PERSON))
        .thenReturn(false);

    service.checkAndAlert();

    ArgumentCaptor<AmlThresholdAlertEvent> eventCaptor =
        ArgumentCaptor.forClass(AmlThresholdAlertEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    AmlThresholdAlertEvent event = eventCaptor.getValue();
    assertThat(event.getType()).isEqualTo(III_PILLAR_DEPOSIT_PERSON);
    assertThat(event.getPersonalId()).isEqualTo("38001010000");
    assertThat(event.getAmount()).isEqualByComparingTo("6001.00");
    assertThat(event.getReference()).isEqualTo("7");

    ArgumentCaptor<AmlThirdPillarAlert> alertCaptor =
        ArgumentCaptor.forClass(AmlThirdPillarAlert.class);
    verify(alertRepository).save(alertCaptor.capture());
    AmlThirdPillarAlert saved = alertCaptor.getValue();
    assertThat(saved.getTransactionFingerprint()).isEqualTo(fingerprint);
    assertThat(saved.getAlertType()).isEqualTo(III_PILLAR_DEPOSIT_PERSON);
    assertThat(saved.getAlertedAt()).isEqualTo(clock.instant());
  }

  @Test
  @DisplayName("does not re-alert a transaction that was already alerted")
  void checkAndAlert_alreadyAlerted_skips() {
    var transaction = qualifyingTransaction();
    var fingerprint = ThirdPillarTransactionFingerprint.of(transaction);
    when(transactionRepository.findByReportingDateGreaterThanEqual(any()))
        .thenReturn(List.of(transaction));
    when(evaluator.evaluate(transaction)).thenReturn(List.of(III_PILLAR_DEPOSIT_PERSON));
    when(alertRepository.existsByTransactionFingerprintAndAlertType(
            fingerprint, III_PILLAR_DEPOSIT_PERSON))
        .thenReturn(true);

    service.checkAndAlert();

    verify(eventPublisher, never()).publishEvent(any());
    verify(alertRepository, never()).save(any());
  }

  @Test
  @DisplayName("does not persist a tracking row and does not throw when the alert send fails")
  void checkAndAlert_sendFails_doesNotTrackAndDoesNotThrow() {
    var transaction = qualifyingTransaction();
    when(transactionRepository.findByReportingDateGreaterThanEqual(any()))
        .thenReturn(List.of(transaction));
    when(evaluator.evaluate(transaction)).thenReturn(List.of(III_PILLAR_DEPOSIT_PERSON));
    when(alertRepository.existsByTransactionFingerprintAndAlertType(any(), any()))
        .thenReturn(false);
    org.mockito.Mockito.doThrow(new IllegalStateException("Slack down"))
        .when(eventPublisher)
        .publishEvent(any());

    assertThatCode(() -> service.checkAndAlert()).doesNotThrowAnyException();

    verify(alertRepository, never()).save(any());
  }

  @Test
  @DisplayName("does nothing for a transaction that does not qualify for any threshold")
  void checkAndAlert_nonQualifying_doesNothing() {
    var transaction = exampleTransaction();
    transaction.setId(9L);
    when(transactionRepository.findByReportingDateGreaterThanEqual(any()))
        .thenReturn(List.of(transaction));
    when(evaluator.evaluate(transaction)).thenReturn(List.of());

    service.checkAndAlert();

    verify(eventPublisher, never()).publishEvent(any());
    verify(alertRepository, never()).save(any());
    verify(alertRepository, never())
        .existsByTransactionFingerprintAndAlertType(any(), eq(III_PILLAR_DEPOSIT_PERSON));
  }
}
