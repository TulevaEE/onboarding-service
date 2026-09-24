package ee.tuleva.onboarding.auth.smartid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class RememberedBrowserPurgeJob {

  private final RememberedBrowsers browsers;

  @Scheduled(cron = "0 25 3 * * *", zone = "Europe/Tallinn")
  void eraseBrowsersPastTheirValidity() {
    int erased = browsers.removeExpired();
    log.info("Erased Smart-ID browsers past their validity: erased={}", erased);
  }
}
