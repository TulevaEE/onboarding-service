package ee.tuleva.onboarding.swedbank.reconcillation;

import ee.tuleva.onboarding.swedbank.statement.SwedbankBankStatementExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SwedbankCheckingReconciliator {
  private final SwedbankBankStatementExtractor swedbankBankStatementExtractor;
  private final Reconciliator reconciliator;

  public void processMessage(String rawResponse) {
    var statement = swedbankBankStatementExtractor.extractFromHistoricStatement(rawResponse);

    try {
      reconciliator.reconcile(statement);
    } catch (Exception e) {
      log.error("Failed reconciliation", e);
    }
  }
}
