package ee.tuleva.onboarding.aml.health;

import ee.tuleva.onboarding.aml.AmlCheckType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledAmlHealthCheckJob {

  private final AmlHealthSanityService amlHealthSanityService;

  private static final Set<AmlCheckType> SKIPPED_CHECK_TYPES =
      Set.of(
          AmlCheckType.POLITICALLY_EXPOSED_PERSON_OVERRIDE,
          AmlCheckType.RISK_LEVEL_OVERRIDE,
          AmlCheckType.RISK_LEVEL_OVERRIDE_CONFIRMATION,
          AmlCheckType.SANCTION_OVERRIDE);

  @Scheduled(cron = "0 0 * * * ?", zone = "Europe/Tallinn")
  public void checkForDelayedAmlChecks() {
    log.info("Starting hourly check for delayed AML processes.");
    try {
      Stream<AmlCheckType> checkTypesToMonitor =
          Arrays.stream(AmlCheckType.values()).filter(type -> !SKIPPED_CHECK_TYPES.contains(type));

      int delayCount = 0;
      for (AmlCheckType checkType : checkTypesToMonitor.toList()) {
        try {
          if (amlHealthSanityService.isCheckTypeDelayed(checkType)) {
            log.error("AML Health Alert: Check type '{}' is delayed.", checkType.name());
            delayCount++;
          }
        } catch (Exception e) {
          // Catch exceptions for individual check type processing to ensure the job continues
          log.error(
              "Error while checking AML health for type {}: {}",
              checkType.name(),
              e.getMessage(),
              e);
        }
      }

      if (delayCount > 0) {
        log.warn("Hourly AML health check completed. Found {} delayed check type(s).", delayCount);
      } else {
        log.info("Hourly AML health check completed successfully. No delays found.");
      }

    } catch (Exception e) {
      log.error("Scheduled AML health check job failed during execution: {}", e.getMessage(), e);
    }
  }
}
