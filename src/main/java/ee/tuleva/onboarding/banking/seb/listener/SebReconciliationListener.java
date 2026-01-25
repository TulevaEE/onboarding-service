package ee.tuleva.onboarding.banking.seb.listener;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.seb.listener.SebEventListenerOrder.RECONCILE;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.banking.seb.reconciliation.SebReconciliator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

@Slf4j
@RequiredArgsConstructor
public class SebReconciliationListener {

  private final SebReconciliator reconciliator;

  @Order(RECONCILE)
  @EventListener
  public void reconcile(BankStatementReceived event) {
    if (event.bankType() != SEB) {
      return;
    }
    if (event.statement().getType() != HISTORIC_STATEMENT) {
      return;
    }
    try {
      reconciliator.reconcile(event.statement());
    } catch (Exception e) {
      log.error("Failed reconciliation: messageId={}", event.messageId(), e);
    }
  }
}
