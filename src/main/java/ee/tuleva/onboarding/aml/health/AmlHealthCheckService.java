package ee.tuleva.onboarding.aml.health;

import static java.time.ZoneOffset.UTC;

import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AmlHealthCheckService {

  private static final double GRACE_PERIOD_PERCENTAGE = 2.0;

  private final AmlHealthThresholdCache amlHealthThresholdCache;
  private final AmlCheckHealthRepository amlCheckHealthRepository;

  public AmlHealthCheckService(
      AmlHealthThresholdCache amlHealthThresholdCache,
      AmlCheckHealthRepository amlCheckHealthRepository) {
    this.amlHealthThresholdCache = amlHealthThresholdCache;
    this.amlCheckHealthRepository = amlCheckHealthRepository;
  }

  public boolean isCheckTypeDelayed(AmlCheckType checkTypeEnum) {
    String checkType = checkTypeEnum.name();
    Instant currentTime = ClockHolder.clock().instant();

    Optional<Duration> thresholdOptional = amlHealthThresholdCache.getThreshold(checkType);

    if (thresholdOptional.isEmpty()) {
      log.warn(
          "No AML health threshold defined for check type: {}. Assuming not delayed.", checkType);
      return false;
    }
    Duration baseThreshold = thresholdOptional.get();

    long baseNanos = baseThreshold.toNanos();
    long graceNanos = (long) (baseNanos * GRACE_PERIOD_PERCENTAGE);
    Duration effectiveThreshold = baseThreshold.plusNanos(graceNanos);

    Optional<Instant> lastCheckTimeOptional =
        amlCheckHealthRepository.findLastCheckTimeByType(checkType).map(ldt -> ldt.toInstant(UTC));

    if (lastCheckTimeOptional.isEmpty()) {
      log.warn(
          "AML check type: {} has never run (for health check). Considering it delayed as a threshold exists (original threshold: {}s).",
          checkType,
          baseThreshold.toSeconds());
      return true;
    }
    Instant lastCheckTime = lastCheckTimeOptional.get();

    Duration timeSinceLastCheck = Duration.between(lastCheckTime, currentTime);

    boolean isDelayed = timeSinceLastCheck.compareTo(effectiveThreshold) > 0;

    if (isDelayed) {
      log.warn(
          "AML check type: {} IS DELAYED for health check. Last check: {}, Current time: {}, Time since last check: {}s, Base Threshold: {}s, Effective Threshold (w/{}% grace): {}s",
          checkType,
          lastCheckTime,
          currentTime,
          timeSinceLastCheck.toSeconds(),
          baseThreshold.toSeconds(),
          (int) (GRACE_PERIOD_PERCENTAGE * 100),
          effectiveThreshold.toSeconds());
    } else {
      log.debug(
          "AML check type: {} is not delayed for health check. Time since last check: {}s, Base Threshold: {}s, Effective Threshold (w/{}% grace): {}s",
          checkType,
          timeSinceLastCheck.toSeconds(),
          baseThreshold.toSeconds(),
          (int) (GRACE_PERIOD_PERCENTAGE * 100),
          effectiveThreshold.toSeconds());
    }
    return isDelayed;
  }
}
