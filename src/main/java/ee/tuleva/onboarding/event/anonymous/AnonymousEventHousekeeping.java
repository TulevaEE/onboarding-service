package ee.tuleva.onboarding.event.anonymous;

import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AnonymousEventHousekeeping {

  static final Duration RETENTION = Duration.ofDays(395);

  private final AnonymousEventRepository repository;
  private final Clock clock;

  @Scheduled(cron = "0 40 3 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "AnonymousEventHousekeeping_purge",
      lockAtMostFor = "10m",
      lockAtLeastFor = "1m")
  void purge() {
    int deleted = repository.deleteOlderThan(clock.instant().minus(RETENTION));
    log.info("Deleted anonymous events older than 13 months: count={}", deleted);
  }
}
