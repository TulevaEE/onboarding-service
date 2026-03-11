package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SebEodFetchNotifier {

  private final OperationsNotificationService notificationService;

  @EventListener
  public void onFetchFailed(SebEodFetchFailedEvent event) {
    try {
      var message =
          "SEB EOD fetch failed: account=%s, error=%s"
              .formatted(event.account(), event.errorMessage());
      notificationService.sendMessage(message, SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send SEB EOD fetch failure notification", e);
    }
  }
}
