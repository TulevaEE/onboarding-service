package ee.tuleva.onboarding.swedbank.listener;

import static ee.tuleva.onboarding.banking.BankType.SWEDBANK;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.swedbank.processor.SwedbankBankStatementProcessor;
import ee.tuleva.onboarding.swedbank.reconcillation.Reconciliator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SwedbankBankStatementListener {

  private final SwedbankBankStatementProcessor processor;
  private final Reconciliator reconciliator;

  @EventListener
  @Transactional
  public void onBankStatementReceived(BankStatementReceived event) {
    if (event.bankType() != SWEDBANK) {
      return;
    }

    processor.processStatement(event.statement());

    if (event.statement().getType() == HISTORIC_STATEMENT) {
      try {
        reconciliator.reconcile(event.statement());
      } catch (Exception e) {
        log.error("Failed reconciliation: messageId={}", event.messageId(), e);
      }
    }
  }
}
