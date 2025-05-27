package ee.tuleva.onboarding.audit.health;

import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditHealthService {

  private final AuditHealthRepository auditHealthRepository;
  private Duration maxIntervalThreshold = Duration.ZERO;

  private static final long THRESHOLD_CALCULATION_PERIOD_DAYS = 10;
  private static final double GRACE_PERIOD_MULTIPLIER = 0.20;

  @PostConstruct
  public void initializeOrRefreshThreshold() {
    log.info(
        "Initializing audit log health threshold based on data from the last {} days.",
        THRESHOLD_CALCULATION_PERIOD_DAYS);
    try {
      Instant sinceTime =
          ClockHolder.clock().instant().minus(THRESHOLD_CALCULATION_PERIOD_DAYS, ChronoUnit.DAYS);
      AuditLogInterval projection =
          auditHealthRepository.findLongestIntervalSecondsSince(sinceTime);

      Double intervalSeconds = projection.getMaxIntervalSeconds();

      if (intervalSeconds != null && intervalSeconds > 0) {
        this.maxIntervalThreshold = Duration.ofSeconds(intervalSeconds.longValue());
        log.info(
            "Successfully initialized audit log health threshold: {} seconds ({}).",
            intervalSeconds.longValue(),
            this.maxIntervalThreshold);
      } else {
        this.maxIntervalThreshold = Duration.ZERO;
        log.warn(
            "Could not determine a positive audit log health threshold from the last {} days (max interval was {} seconds)."
                + " Defaulting to ZERO. Health check will report 'not delayed' unless audit log is empty.",
            THRESHOLD_CALCULATION_PERIOD_DAYS,
            intervalSeconds);
      }
    } catch (Exception e) {
      log.error(
          "Failed to initialize audit log health threshold. Defaulting to ZERO. Error: {}",
          e.getMessage(),
          e);
      this.maxIntervalThreshold = Duration.ZERO;
    }
  }

  public boolean isAuditLogDelayed() {
    Instant currentTime = ClockHolder.clock().instant();

    if (this.maxIntervalThreshold.isZero()) {
      log.warn(
          "Audit log health check running with a ZERO threshold. "
              + "This usually means insufficient historical data, very rapid logging, or no positive intervals found. "
              + "Reporting 'not delayed' unless the log is completely empty.");
    }

    Optional<Instant> lastAuditEventTimeOptional =
        auditHealthRepository.findLastAuditEventTimestamp();

    if (lastAuditEventTimeOptional.isEmpty()) {
      if (!this.maxIntervalThreshold.isZero()) {
        log.warn(
            "Audit log has no entries. Considering it DELAYED as a non-zero threshold ({}) was established.",
            this.maxIntervalThreshold.toSeconds());
        return true;
      } else {
        log.info(
            "Audit log has no entries, and no operational threshold was established (threshold is ZERO). Reporting 'not delayed'.");
        return false;
      }
    }
    Instant lastAuditEventTime = lastAuditEventTimeOptional.get();

    if (this.maxIntervalThreshold.isZero()) {
      log.debug(
          "Audit log has entries, but operational threshold is ZERO. Reporting 'not delayed'.");
      return false;
    }

    Duration baseThreshold = this.maxIntervalThreshold;
    long baseNanos = baseThreshold.toNanos();
    long graceNanos = (long) (baseNanos * GRACE_PERIOD_MULTIPLIER);
    Duration effectiveThreshold = baseThreshold.plusNanos(graceNanos);

    Duration timeSinceLastEvent = Duration.between(lastAuditEventTime, currentTime);
    boolean isDelayed = timeSinceLastEvent.compareTo(effectiveThreshold) > 0;

    if (isDelayed) {
      log.warn(
          "Audit Log Health Alert: Log is DELAYED. Last event: {}, Current time: {}, Time since last event: {}s, Base Threshold: {}s, Effective Threshold (w/{}% grace): {}s",
          lastAuditEventTime,
          currentTime,
          timeSinceLastEvent.toSeconds(),
          baseThreshold.toSeconds(),
          (int) (GRACE_PERIOD_MULTIPLIER * 100),
          effectiveThreshold.toSeconds());
    } else {
      log.info(
          "Audit log health check: OK. Time since last event: {}s, Effective Threshold: {}s",
          timeSinceLastEvent.toSeconds(),
          effectiveThreshold.toSeconds());
    }
    return isDelayed;
  }

  void setMaxIntervalThresholdForTest(Duration threshold) {
    this.maxIntervalThreshold = threshold;
  }
}
