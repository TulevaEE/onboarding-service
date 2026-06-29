package ee.tuleva.onboarding.aml.alert;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TkfVolumeAlertService {

  private final TkfVolumeReader reader;
  private final TkfVolumeEvaluator evaluator;
  private final AmlTkfVolumeAlertRepository alertRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public void checkAndAlert() {
    for (TkfVolumeAggregate aggregate : reader.readVolumeAggregates()) {
      for (TkfVolumeAlert alert : evaluator.evaluate(aggregate)) {
        alertOnce(aggregate, alert);
      }
    }
  }

  private void alertOnce(TkfVolumeAggregate aggregate, TkfVolumeAlert alert) {
    String personalId = aggregate.personalId();
    if (alertRepository.existsByPersonalIdAndAlertTypeAndDirectionAndWindowKey(
        personalId, alert.type(), alert.direction(), alert.windowKey())) {
      return;
    }
    try {
      String reference = alert.direction() + "/" + alert.windowKey();
      eventPublisher.publishEvent(
          new AmlThresholdAlertEvent(
              this, alert.type(), personalId, alert.amount(), reference, aggregate.partyType()));
      alertRepository.save(
          AmlTkfVolumeAlert.builder()
              .personalId(personalId)
              .alertType(alert.type())
              .direction(alert.direction())
              .windowKey(alert.windowKey())
              .alertedAt(clock.instant())
              .build());
    } catch (Exception e) {
      log.error(
          "Failed to send TKF volume AML alert: alertType={}, direction={}, window={}",
          alert.type(),
          alert.direction(),
          alert.windowKey(),
          e);
    }
  }
}
