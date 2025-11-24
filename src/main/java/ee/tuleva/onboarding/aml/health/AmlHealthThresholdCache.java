package ee.tuleva.onboarding.aml.health;

import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AmlHealthThresholdCache {

  private final AmlCheckHealthRepository amlCheckHealthRepository;
  private final Map<String, Duration> thresholds = new ConcurrentHashMap<>();

  private static final String REFRESH_CRON_EXPRESSION = "0 0 1 * * *";
  private static final String REFRESH_CRON_ZONE = "Europe/Tallinn";

  public AmlHealthThresholdCache(AmlCheckHealthRepository amlCheckHealthRepository) {
    this.amlCheckHealthRepository = amlCheckHealthRepository;
  }

  @PostConstruct
  @Scheduled(cron = REFRESH_CRON_EXPRESSION, zone = REFRESH_CRON_ZONE)
  @SchedulerLock(
      name = "AmlHealthThresholdCache_refreshThresholds",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void refreshThresholds() {
    log.info(
        "Refreshing AML health thresholds using cron: [{}] in zone: [{}]",
        REFRESH_CRON_EXPRESSION,
        REFRESH_CRON_ZONE);
    try {
      Instant oneMonthAgo = ClockHolder.clock().instant().minus(30, ChronoUnit.DAYS);

      List<AmlCheckTypeHealthThreshold> results =
          amlCheckHealthRepository.findMaxIntervalSecondsPerTypeSince(oneMonthAgo);
      Map<String, Duration> newThresholds = new ConcurrentHashMap<>();
      for (AmlCheckTypeHealthThreshold thresholdDto : results) {
        if (thresholdDto.getType() != null && thresholdDto.getMaxIntervalSeconds() != null) {
          long seconds = (long) thresholdDto.getMaxIntervalSeconds().doubleValue();
          if (seconds < 0) {
            log.warn(
                "Negative max_interval_seconds ({}) for type {}. Skipping.",
                seconds,
                thresholdDto.getType());
            continue;
          }
          newThresholds.put(thresholdDto.getType(), Duration.ofSeconds(seconds));
        }
      }
      thresholds.clear();
      thresholds.putAll(newThresholds);
      log.info("Successfully refreshed AML health thresholds. Loaded {} types.", thresholds.size());
    } catch (Exception e) {
      log.error("Failed to refresh AML health thresholds due to an unexpected error.", e);
    }
  }

  public Optional<Duration> getThreshold(String checkType) {
    return Optional.ofNullable(thresholds.get(checkType));
  }

  public Map<String, Duration> getAllThresholds() {
    return new ConcurrentHashMap<>(thresholds);
  }

  public void updateThresholdsForTest(Map<String, Duration> testThresholds) {
    thresholds.clear();
    thresholds.putAll(testThresholds);
  }
}
