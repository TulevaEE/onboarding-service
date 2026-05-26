package ee.tuleva.onboarding.savings.fund.documents;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SavingsFundDocumentsService {

  static final SavingsFundDocuments FALLBACK =
      new SavingsFundDocuments(
          "https://tuleva.ee/wp-content/uploads/2026/02/Tuleva.eurofond.tingimused.02.02.2026.pdf",
          "https://tuleva.ee/wp-content/uploads/2026/02/TKF100-Prospekt-kehtib-alates-27.02.2026.pdf",
          "https://tuleva.ee/wp-content/uploads/2026/02/Pohiteave-TKF100-kehtib-alates-27.02.2026.pdf");

  private static final Duration STALENESS_THRESHOLD = Duration.ofHours(48);

  private final WordpressDocumentsClient client;
  private final OperationsNotificationService notificationService;
  private final Clock clock;

  private final AtomicReference<SavingsFundDocuments> current = new AtomicReference<>(FALLBACK);
  private final AtomicReference<Instant> lastSuccessfulRefreshAt;

  public SavingsFundDocumentsService(
      WordpressDocumentsClient client,
      OperationsNotificationService notificationService,
      Clock clock) {
    this.client = client;
    this.notificationService = notificationService;
    this.clock = clock;
    this.lastSuccessfulRefreshAt = new AtomicReference<>(clock.instant());
  }

  public SavingsFundDocuments getDocuments() {
    return current.get();
  }

  public void refresh() {
    try {
      SavingsFundDocuments fetched = client.fetch();
      current.set(fetched);
      lastSuccessfulRefreshAt.set(clock.instant());
      log.info("Refreshed savings fund documents from WordPress: documents={}", fetched);
    } catch (Exception e) {
      log.error(
          "Failed to refresh savings fund documents from WordPress, serving last-known-good:"
              + " error={}",
          e.getMessage(),
          e);
      alert(
          "Failed to refresh savings fund documents from WordPress, serving last-known-good:"
              + " error="
              + e.getMessage());
      alertIfStale();
    }
  }

  private void alertIfStale() {
    Instant lastSuccess = lastSuccessfulRefreshAt.get();
    Duration sinceSuccess = Duration.between(lastSuccess, clock.instant());
    if (sinceSuccess.compareTo(STALENESS_THRESHOLD) > 0) {
      alert(
          "Savings fund documents have not refreshed from WordPress for "
              + sinceSuccess.toHours()
              + "h, still serving last-known-good: lastSuccessfulRefreshAt="
              + lastSuccess);
    }
  }

  private void alert(String message) {
    try {
      notificationService.sendMessage(message, SAVINGS);
    } catch (Exception alertError) {
      log.error("Failed to send savings fund documents alert", alertError);
    }
  }
}
