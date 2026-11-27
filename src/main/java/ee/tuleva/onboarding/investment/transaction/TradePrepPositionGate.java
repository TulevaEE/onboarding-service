package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.investment.transaction.InputGateFailure.HoldingMismatch;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoRegistryHoldings;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoReportSecurities;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.UnquantifiedReportHoldings;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.Compared;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.HoldingDifference;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.RegistryHasNoHoldings;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.ReportHasNoSecurities;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.ReportRowsWithoutQuantity;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TradePrepPositionGate {

  private final PositionReconciliation positionReconciliation;

  PositionCheck check(TulevaFund fund, LocalDate positionDate, TradePrepOverrides overrides) {
    List<String> accepted = overrides.acceptedDifferenceIsins();
    return switch (positionReconciliation.againstPositionReport(
        fund, positionDate, overrides.excludedIsins())) {
      case Compared compared ->
          afterAccepting(
              compared.differences(), accepted, unaccepted -> mismatches(unaccepted, positionDate));
      case RegistryHasNoHoldings noHoldings ->
          afterAccepting(
              noHoldings.differences(),
              accepted,
              _ -> List.of(new NoRegistryHoldings(positionDate)));
      case ReportHasNoSecurities _ ->
          new PositionCheck(List.of(new NoReportSecurities(positionDate)), notNeeded(accepted));
      case ReportRowsWithoutQuantity unquantified ->
          new PositionCheck(
              List.of(new UnquantifiedReportHoldings(positionDate, unquantified.isins())),
              notNeeded(accepted));
    };
  }

  private static PositionCheck afterAccepting(
      List<HoldingDifference> differences,
      List<String> accepted,
      Function<List<HoldingDifference>, List<InputGateFailure>> stop) {
    List<HoldingDifference> unaccepted =
        differences.stream().filter(difference -> !accepted.contains(difference.isin())).toList();
    return new PositionCheck(
        unaccepted.isEmpty() ? List.of() : stop.apply(unaccepted),
        accepted.stream().map(isin -> acceptedDifference(isin, differences)).toList());
  }

  private static AcceptedHoldingDifference acceptedDifference(
      String isin, List<HoldingDifference> differences) {
    return differences.stream()
        .filter(difference -> difference.isin().equals(isin))
        .findFirst()
        .map(
            difference ->
                new AcceptedHoldingDifference(
                    isin,
                    GateNeed.CLEARED_A_STOP,
                    difference.registryQuantity(),
                    difference.reportQuantity()))
        .orElseGet(() -> new AcceptedHoldingDifference(isin, GateNeed.NOT_NEEDED, null, null));
  }

  private static List<AcceptedHoldingDifference> notNeeded(List<String> accepted) {
    return accepted.stream()
        .map(isin -> new AcceptedHoldingDifference(isin, GateNeed.NOT_NEEDED, null, null))
        .toList();
  }

  private static List<InputGateFailure> mismatches(
      List<HoldingDifference> differences, LocalDate positionDate) {
    return differences.stream()
        .<InputGateFailure>map(
            difference ->
                new HoldingMismatch(
                    difference.isin(),
                    positionDate,
                    difference.registryQuantity(),
                    difference.reportQuantity()))
        .toList();
  }
}
