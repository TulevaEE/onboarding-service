package ee.tuleva.onboarding.banking.seb.reconciliation;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationNotifier {

  private final OperationsNotificationService notificationService;

  @EventListener
  public void onReconciliationCompleted(ReconciliationCompletedEvent event) {
    try {
      var message =
          event.matched()
              ? "Bank reconciliation OK: bankAccount=%s, balance=%s"
                  .formatted(event.bankAccount(), event.bankBalance())
              : "Bank reconciliation FAILED: bankAccount=%s, bankBalance=%s, ledgerBalance=%s, diff=%s"
                  .formatted(
                      event.bankAccount(),
                      event.bankBalance(),
                      event.ledgerBalance(),
                      event.bankBalance().subtract(event.ledgerBalance()).abs());
      notificationService.sendMessage(message, SAVINGS);
    } catch (Exception e) {
      log.error("Failed to send reconciliation notification", e);
    }
  }
}
