package ee.tuleva.onboarding.swedbank.listener;

import static ee.tuleva.onboarding.banking.BankType.SWEDBANK;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.swedbank.listener.SwedbankEventListenerOrder.RECONCILE;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.swedbank.reconcillation.Reconciliator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

@Slf4j
@RequiredArgsConstructor
public class SwedbankReconciliationListener {

  private final Reconciliator reconciliator;

  @Order(RECONCILE)
  @EventListener
  public void reconcile(BankStatementReceived event) {
    if (event.bankType() != SWEDBANK) {
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
