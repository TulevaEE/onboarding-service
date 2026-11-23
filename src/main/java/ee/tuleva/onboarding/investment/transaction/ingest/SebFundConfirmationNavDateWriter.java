package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class SebFundConfirmationNavDateWriter {

  private final SebFundConfirmationRepository confirmationRepository;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;
  private final SebFundConfirmationAuditRecorder auditRecorder;

  @Transactional
  void write(String tradeReference) {
    executionRepository
        .findByBrokerTransactionId(tradeReference)
        .ifPresent(
            execution ->
                orderRepository
                    .findById(execution.getOrderId())
                    .ifPresent(order -> writeTo(execution, order)));
  }

  void writeTo(TransactionExecution execution, TransactionOrder order) {
    Optional.ofNullable(execution.getBrokerTransactionId())
        .flatMap(confirmationRepository::findByTradeReference)
        .filter(confirmation -> confirmation.isTheTradeOf(order))
        .filter(confirmation -> !confirmation.pricingDate().equals(execution.getNavDate()))
        .ifPresent(confirmation -> setNavDate(execution, confirmation));
  }

  private void setNavDate(
      TransactionExecution execution, RecordedSebFundConfirmation confirmation) {
    LocalDate replaced = execution.getNavDate();
    execution.setNavDate(confirmation.pricingDate());
    executionRepository.save(execution);
    auditRecorder.recordNavDateSet(confirmation, execution, replaced);
  }
}
