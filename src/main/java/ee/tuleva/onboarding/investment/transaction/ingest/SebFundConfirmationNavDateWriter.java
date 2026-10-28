package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class SebFundConfirmationNavDateWriter {

  private final SebFundConfirmationRegistryCheck registryCheck;
  private final TransactionExecutionRepository executionRepository;
  private final SebFundConfirmationAuditRecorder auditRecorder;

  @Transactional
  void write(SebFundConfirmationOutcome outcome) {
    if (!outcome.result().confirmsTheReportedTrade()) {
      return;
    }
    SebFundConfirmation confirmation = outcome.confirmation();
    registryCheck
        .executionOfTheSameTrade(confirmation)
        .filter(execution -> !confirmation.pricingDate().equals(execution.getNavDate()))
        .ifPresent(execution -> setNavDate(execution, confirmation));
  }

  private void setNavDate(TransactionExecution execution, SebFundConfirmation confirmation) {
    LocalDate replaced = execution.getNavDate();
    execution.setNavDate(confirmation.pricingDate());
    executionRepository.save(execution);
    auditRecorder.recordNavDateSet(confirmation, execution, replaced);
  }
}
