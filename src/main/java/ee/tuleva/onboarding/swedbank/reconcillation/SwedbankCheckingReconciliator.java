package ee.tuleva.onboarding.swedbank.reconcillation;

import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SwedbankCheckingReconciliator {
  private final BankStatementExtractor bankStatementExtractor;
  private final Reconciliator reconciliator;

  public void processMessage(String rawResponse, ZoneId timezone) {
    var statement = bankStatementExtractor.extractFromHistoricStatement(rawResponse, timezone);

    try {
      reconciliator.reconcile(statement);
    } catch (Exception e) {
      log.error("Failed reconciliation", e);
    }
  }
}
