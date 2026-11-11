package ee.tuleva.onboarding.aml.risklevel;

import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobFailedEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"!dev & !staging"})
public class ScheduledRiskLevelCheckJob {

  private final RiskLevelService riskLevelService;
  private final AmlServiceRiskLevels amlServiceRiskLevels;
  private final ApplicationEventPublisher eventPublisher;

  @Scheduled(cron = "0 30 5 * * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledRiskLevelCheckJob_runFromAmlService",
      lockAtMostFor = "1h",
      lockAtLeastFor = "30m")
  public void runFromAmlService() {
    log.info("Starting AML risk level check job from aml-service");
    runFromAmlService(
        "III pillar",
        amlServiceRiskLevels::fetchThirdPillar,
        riskLevelService::writeThirdPillarRiskLevels);
    runFromAmlService("TKF", amlServiceRiskLevels::fetchTkf, riskLevelService::writeTkfRiskLevels);
    log.info("Finished AML risk level check job from aml-service");
  }

  private void runFromAmlService(
      String pillar, Supplier<AssessedRiskLevels> fetch, Consumer<AssessedRiskLevels> write) {
    try {
      write.accept(fetch.get());
    } catch (Exception e) {
      log.error("Risk level check failed: pillar={}", pillar, e);
      reportFailure(new AmlRiskLevelJobFailedEvent(pillar, slackSafeReason(e)));
    }
  }

  private void reportFailure(AmlRiskLevelJobFailedEvent failure) {
    try {
      eventPublisher.publishEvent(failure);
    } catch (Exception e) {
      log.error("Risk level check failure could not be reported: pillar={}", failure.label(), e);
    }
  }

  private static String slackSafeReason(Exception failure) {
    return switch (failure) {
      case RestClientResponseException response -> "HTTP " + response.getStatusCode().value();
      case RiskLevelsRefusedException refused -> refused.reason();
      default -> failure.getClass().getSimpleName();
    };
  }
}
