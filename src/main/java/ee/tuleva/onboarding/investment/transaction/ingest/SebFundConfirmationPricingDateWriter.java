package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class SebFundConfirmationPricingDateWriter {

  private final TransactionExecutionRepository executionRepository;
  private final SebFundConfirmationAuditRecorder auditRecorder;

  @Transactional
  void write(SebFundConfirmation confirmation) {
    executionRepository
        .findByBrokerTransactionId(confirmation.tradeReference())
        .filter(execution -> !confirmation.pricingDate().equals(execution.getPricingDate()))
        .ifPresent(execution -> setPricingDate(execution, confirmation));
  }

  private void setPricingDate(TransactionExecution execution, SebFundConfirmation confirmation) {
    LocalDate replaced = execution.getPricingDate();
    execution.setPricingDate(confirmation.pricingDate());
    executionRepository.save(execution);
    auditRecorder.recordPricingDateSet(confirmation, execution, replaced);
  }
}
