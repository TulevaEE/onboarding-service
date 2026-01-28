package ee.tuleva.onboarding.banking.seb.listener;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.seb.listener.SebEventListenerOrder.PROCESS_STATEMENT;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.banking.seb.processor.SebBankStatementProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class SebBankStatementListener {

  private final SebBankStatementProcessor processor;

  @Order(PROCESS_STATEMENT)
  @EventListener
  @Transactional
  public void processStatement(BankStatementReceived event) {
    if (event.bankType() != SEB) {
      return;
    }
    processor.processStatement(event.statement(), event.messageId());
  }
}
