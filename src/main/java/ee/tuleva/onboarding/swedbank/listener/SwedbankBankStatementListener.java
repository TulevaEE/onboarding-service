package ee.tuleva.onboarding.swedbank.listener;

import static ee.tuleva.onboarding.banking.BankType.SWEDBANK;
import static ee.tuleva.onboarding.swedbank.listener.SwedbankEventListenerOrder.PROCESS_STATEMENT;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.swedbank.processor.SwedbankBankStatementProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class SwedbankBankStatementListener {

  private final SwedbankBankStatementProcessor processor;

  @Order(PROCESS_STATEMENT)
  @EventListener
  @Transactional
  public void processStatement(BankStatementReceived event) {
    if (event.bankType() != SWEDBANK) {
      return;
    }
    processor.processStatement(event.statement());
  }
}
