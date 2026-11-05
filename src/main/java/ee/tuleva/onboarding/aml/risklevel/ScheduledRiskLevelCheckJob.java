package ee.tuleva.onboarding.aml.risklevel;

import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobFailedEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobFellBackEvent;
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
@Profile("!dev")
public class ScheduledRiskLevelCheckJob {

  private final RiskLevelService riskLevelService;
  private final AmlServiceRiskLevels amlServiceRiskLevels;
  private final AmlRiskReader amlRiskReader;
  private final TkfRiskReader tkfRiskReader;
  private final ApplicationEventPublisher eventPublisher;
  private final AmlServiceProperties amlServiceProperties;

  private static final double MONTHLY_MEDIUM_RISK_TARGET_PROBABILITY = 0.025;
  private static final double DAYS_IN_MONTH = 30.0;
  private static final double MEDIUM_SAMPLE_PROBABILITY_FOR_DAILY_RUN =
      MONTHLY_MEDIUM_RISK_TARGET_PROBABILITY / DAYS_IN_MONTH;

  @Scheduled(cron = "0 0 1 * * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledRiskLevelCheckJob_run",
      lockAtMostFor = "1h",
      lockAtLeastFor = "30m")
  public void run() {
    if (amlServiceProperties.enabled()) {
      refreshViewsForAmlService();
      return;
    }
    log.info(
        "Starting AML risk level check job with medium risk sampling probability: {}",
        String.format("%.8f", MEDIUM_SAMPLE_PROBABILITY_FOR_DAILY_RUN));
    try {
      riskLevelService.runRiskLevelCheck(MEDIUM_SAMPLE_PROBABILITY_FOR_DAILY_RUN);
    } catch (Exception e) {
      log.error("III pillar risk level check failed", e);
    }
    try {
      riskLevelService.runTkfRiskLevelCheck(MEDIUM_SAMPLE_PROBABILITY_FOR_DAILY_RUN);
    } catch (Exception e) {
      log.error("TKF risk level check failed", e);
    }
    log.info("Finished AML risk level check job");
  }

  @Scheduled(cron = "0 30 5 * * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledRiskLevelCheckJob_runFromAmlService",
      lockAtMostFor = "1h",
      lockAtLeastFor = "30m")
  public void runFromAmlService() {
    if (!amlServiceProperties.enabled()) {
      return;
    }
    log.info("Starting AML risk level check job from aml-service");
    runFromAmlService(
        "III pillar",
        amlServiceRiskLevels::fetchThirdPillar,
        riskLevelService::writeThirdPillarRiskLevels,
        () -> riskLevelService.runRiskLevelCheck(MEDIUM_SAMPLE_PROBABILITY_FOR_DAILY_RUN));
    runFromAmlService(
        "TKF",
        amlServiceRiskLevels::fetchTkf,
        riskLevelService::writeTkfRiskLevels,
        () -> riskLevelService.runTkfRiskLevelCheck(MEDIUM_SAMPLE_PROBABILITY_FOR_DAILY_RUN));
    log.info("Finished AML risk level check job from aml-service");
  }

  private void refreshViewsForAmlService() {
    log.info("Refreshing the risk views aml-service reads, writing no risk levels");
    refresh(
        "analytics.mv_third_pillar_latest_residency", amlRiskReader::refreshLatestResidencyView);
    refresh("analytics.v_aml_risk_metadata", amlRiskReader::refreshAmlRiskMetadataView);
    refresh("analytics.v_tkf_risk_metadata", tkfRiskReader::refreshMaterializedView);
  }

  private void refresh(String view, Runnable refresh) {
    try {
      refresh.run();
    } catch (Exception e) {
      log.error("Risk view refresh failed: view={}", view, e);
    }
  }

  private void runFromAmlService(
      String pillar,
      Supplier<AssessedRiskLevels> fetch,
      Consumer<AssessedRiskLevels> write,
      Runnable fromViews) {
    AssessedRiskLevels riskLevels;
    try {
      riskLevels = fetch.get();
    } catch (Exception e) {
      log.error(
          "Risk levels from aml-service refused, falling back to the views: pillar={}", pillar, e);
      report(pillar, new AmlRiskLevelJobFellBackEvent(pillar, slackSafeReason(e)));
      runOrReportFailure(pillar, fromViews);
      return;
    }
    runOrReportFailure(pillar, () -> write.accept(riskLevels));
  }

  private void runOrReportFailure(String pillar, Runnable check) {
    try {
      check.run();
    } catch (Exception e) {
      log.error("Risk level check failed: pillar={}", pillar, e);
      report(pillar, new AmlRiskLevelJobFailedEvent(pillar, slackSafeReason(e)));
    }
  }

  private void report(String pillar, Object event) {
    try {
      eventPublisher.publishEvent(event);
    } catch (Exception e) {
      log.error("Risk level check outcome could not be reported: pillar={}", pillar, e);
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
