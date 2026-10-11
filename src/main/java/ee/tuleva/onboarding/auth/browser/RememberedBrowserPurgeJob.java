package ee.tuleva.onboarding.auth.browser;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class RememberedBrowserPurgeJob {

  private final List<ExpiringRememberedEntries> rememberedEntries;
  private final RememberedBrowsers browsers;

  @Scheduled(cron = "0 25 3 * * *", zone = "Europe/Tallinn")
  void eraseWhatIsPastItsValidity() {
    int entries =
        rememberedEntries.stream().mapToInt(ExpiringRememberedEntries::removeExpired).sum();
    int erasedBrowsers = browsers.removeExpired();
    log.info(
        "Erased remembered logins past their validity: entries={}, browsers={}",
        entries,
        erasedBrowsers);
  }
}
