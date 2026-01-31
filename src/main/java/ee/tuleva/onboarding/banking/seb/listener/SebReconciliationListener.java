package ee.tuleva.onboarding.banking.seb.listener;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.seb.listener.SebEventListenerOrder.RECONCILE;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.banking.seb.reconciliation.SebReconciliator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;

@Slf4j
@RequiredArgsConstructor
public class SebReconciliationListener {

  private final SebReconciliator reconciliator;
  private final TaskScheduler taskScheduler;
  private final Clock clock;
  private final Duration reconciliationDelay;

  @Order(RECONCILE)
  @EventListener
  public void reconcile(BankStatementReceived event) {
    if (event.bankType() != SEB) {
      return;
    }
    if (event.statement().getType() != HISTORIC_STATEMENT) {
      return;
    }

    Instant scheduledTime = clock.instant().plus(reconciliationDelay);
    log.info(
        "Scheduling reconciliation: messageId={}, delay={}",
        event.messageId(),
        reconciliationDelay);
    taskScheduler.schedule(() -> executeReconciliation(event), scheduledTime);
  }

  private void executeReconciliation(BankStatementReceived event) {
    try {
      reconciliator.reconcile(event.statement());
    } catch (Exception e) {
      log.error("Failed reconciliation: messageId={}", event.messageId(), e);
    }
  }
}
