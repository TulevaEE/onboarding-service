package ee.tuleva.onboarding.aml.alert;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransaction;
import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPillarAlertService {

  private static final int LOOKBACK_DAYS = 40;

  private final AnalyticsThirdPillarTransactionRepository transactionRepository;
  private final AmlThirdPillarAlertRepository alertRepository;
  private final ThirdPillarAlertEvaluator evaluator;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public void checkAndAlert() {
    LocalDate cutoff = LocalDate.now(clock).minusDays(LOOKBACK_DAYS);
    var transactions = transactionRepository.findByReportingDateGreaterThanEqual(cutoff);
    for (AnalyticsThirdPillarTransaction transaction : transactions) {
      for (AmlAlertType alertType : evaluator.evaluate(transaction)) {
        alertOnce(transaction, alertType);
      }
    }
  }

  private void alertOnce(AnalyticsThirdPillarTransaction transaction, AmlAlertType alertType) {
    if (alertRepository.existsByTransactionIdAndAlertType(transaction.getId(), alertType)) {
      return;
    }
    try {
      eventPublisher.publishEvent(
          new AmlThresholdAlertEvent(
              this,
              alertType,
              transaction.getPersonalId(),
              transaction.getTransactionValue(),
              String.valueOf(transaction.getId())));
      alertRepository.save(
          AmlThirdPillarAlert.builder()
              .transactionId(transaction.getId())
              .alertType(alertType)
              .alertedAt(clock.instant())
              .build());
    } catch (Exception e) {
      log.error(
          "Failed to send III pillar AML alert: transactionId={}, alertType={}",
          transaction.getId(),
          alertType,
          e);
    }
  }
}
