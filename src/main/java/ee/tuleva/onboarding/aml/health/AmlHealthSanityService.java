package ee.tuleva.onboarding.aml.health;

import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AmlHealthSanityService {

  private final AmlHealthThresholdCache amlHealthThresholdCache;
  private final AmlCheckHealthRepository amlCheckHealthRepository;

  public AmlHealthSanityService(
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

    // Calculate effective threshold with 5% grace period
    long baseNanos = baseThreshold.toNanos();
    long graceNanos = (long) (baseNanos * 0.05);
    Duration effectiveThreshold = baseThreshold.plusNanos(graceNanos);

    Optional<Instant> lastCheckTimeOptional =
        amlCheckHealthRepository.findLastCheckTimeByType(checkType);

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
          "AML check type: {} IS DELAYED for health check. Last check: {}, Current time: {}, Time since last check: {}s, Base Threshold: {}s, Effective Threshold (w/5% grace): {}s",
          checkType,
          lastCheckTime,
          currentTime,
          timeSinceLastCheck.toSeconds(),
          baseThreshold.toSeconds(),
          effectiveThreshold.toSeconds());
    } else {
      log.debug(
          "AML check type: {} is not delayed for health check. Time since last check: {}s, Base Threshold: {}s, Effective Threshold (w/5% grace): {}s",
          checkType,
          timeSinceLastCheck.toSeconds(),
          baseThreshold.toSeconds(),
          effectiveThreshold.toSeconds());
    }
    return isDelayed;
  }
}
