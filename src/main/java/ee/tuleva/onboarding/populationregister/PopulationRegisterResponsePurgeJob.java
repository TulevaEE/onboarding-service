package ee.tuleva.onboarding.populationregister;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class PopulationRegisterResponsePurgeJob {

  static final Duration RETENTION = Duration.ofDays(365);

  private final PopulationRegisterResponseStore store;

  @Scheduled(cron = "0 15 3 * * *", zone = "Europe/Tallinn")
  void erasePersonalDataPastRetention() {
    int erased = store.eraseResponsesOlderThan(RETENTION);
    log.info(
        "Erased population register responses past retention: retentionDays={}, erased={}",
        RETENTION.toDays(),
        erased);
  }
}
