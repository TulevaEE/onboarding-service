package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.investment.transaction.InputGateFailure.HoldingMismatch;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoRegistryHoldings;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoReportSecurities;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.Compared;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.RegistryHasNoHoldings;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.ReportHasNoSecurities;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TradePrepPositionGate {

  private final PositionReconciliation positionReconciliation;

  List<InputGateFailure> differencesFromPositionReport(TulevaFund fund, LocalDate positionDate) {
    return switch (positionReconciliation.againstPositionReport(fund, positionDate)) {
      case Compared compared ->
          compared.differences().stream()
              .<InputGateFailure>map(
                  difference ->
                      new HoldingMismatch(
                          difference.isin(),
                          positionDate,
                          difference.registryQuantity(),
                          difference.reportQuantity()))
              .toList();
      case RegistryHasNoHoldings _ -> List.of(new NoRegistryHoldings(positionDate));
      case ReportHasNoSecurities _ -> List.of(new NoReportSecurities(positionDate));
    };
  }
}
