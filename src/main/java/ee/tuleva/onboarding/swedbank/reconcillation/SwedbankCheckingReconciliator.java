package ee.tuleva.onboarding.swedbank.reconcillation;

import static ee.tuleva.onboarding.swedbank.SwedbankGatewayTime.SWEDBANK_GATEWAY_TIME_ZONE;

import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SwedbankCheckingReconciliator {
  private final BankStatementExtractor bankStatementExtractor;
  private final Reconciliator reconciliator;

  public void processMessage(String rawResponse) {
    var statement =
        bankStatementExtractor.extractFromHistoricStatement(
            rawResponse, SWEDBANK_GATEWAY_TIME_ZONE);

    try {
      reconciliator.reconcile(statement);
    } catch (Exception e) {
      log.error("Failed reconciliation", e);
    }
  }
}
