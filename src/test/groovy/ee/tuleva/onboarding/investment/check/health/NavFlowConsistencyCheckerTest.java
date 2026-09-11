package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.NAV_FLOW_CONSISTENCY;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.NAV;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.investment.position.AccountType.UNITS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NavFlowConsistencyCheckerTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("0.001");

  private final NavFlowConsistencyChecker checker =
      new NavFlowConsistencyChecker(new MarketPnlCalculator());

  @Test
  void aDayWhereOnlyPricesMovedReconciles() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today = positions(security("IE00A", "10000", "102", "1020000"), units("1000000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD, Map.of())).isEmpty();
  }

  @Test
  void aSubscriptionSettledAtNavReconciles() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today =
        positions(security("IE00A", "10000", "102", "1020000"), cash("102000"), units("1100000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD, Map.of())).isEmpty();
  }

  @Test
  void aRedemptionWhosePayoutWasNeverBookedWarns() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today = positions(security("IE00A", "10000", "100", "1000000"), units("800000"));

    var findings = checker.check(TUK75, today, previous, THRESHOLD, Map.of());

    assertThat(findings).hasSize(1);
    var finding = findings.getFirst();
    assertThat(finding.fund()).isEqualTo(TUK75);
    assertThat(finding.checkType()).isEqualTo(NAV_FLOW_CONSISTENCY);
    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.message())
        .contains("unexplained=250000.00")
        .contains("quantitiesChanged=false");
  }

  // The custodian's own pending-transactions report carries the price the holding actually left
  // at, which for a mutual fund is the dealing NAV and the only same-day mark that exists. With it
  // the day reconciles instead of taking the whole fund's reconciliation down with one line.
  @Test
  void aHoldingSoldOutIsMarkedAtItsExecutedPriceAndReconciles() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today = positions(cash("1020000"), units("1000000"));

    var exitMarks = Map.of("IE00A", new ExitMark(new BigDecimal("102"), null));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD, exitMarks)).isEmpty();
  }

  // Marking the exit at its executed price keeps the dealing cost out of unexplained, so the levy
  // or slippage has to be named against the day's published price or it is invisible.
  @Test
  void aHoldingSoldAwayFromItsPublishedPriceNamesTheDealingCost() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today = positions(cash("980000"), units("1000000"));

    var exitMarks =
        Map.of(
            "IE00A",
            new ExitMark(
                new BigDecimal("99"),
                new ExitMark.PublishedPrice(new BigDecimal("101"), LocalDate.parse("2026-08-25"))));

    var findings = checker.check(TUK75, today, previous, THRESHOLD, exitMarks);

    assertThat(findings).hasSize(1);
    var finding = findings.getFirst();
    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.message())
        .contains("unexplained=-10000.00")
        .contains("exitPrice=99")
        .contains("dealingCost=-20000.00 EUR");
  }

  // A mutual fund's published NAV for the day reaches our price pipeline a business day or more
  // after the gate runs, so the reconciliation must not wait for it - only the attribution does.
  @Test
  void aDealingCostWithNoSameDatePublishedPriceIsReportedAsPending() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today = positions(cash("980000"), units("1000000"));

    var exitMarks = Map.of("IE00A", new ExitMark(new BigDecimal("99"), null));

    var findings = checker.check(TUK75, today, previous, THRESHOLD, exitMarks);

    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().message()).contains("dealingCost=pending");
  }

  // A holding sold out with no execution behind it is a truncated or altered report, not a trade,
  // and a settlement day is exactly when this check matters - so it has to say it could not run
  // rather than return quietly.
  @Test
  void aHoldingSoldOutWithNoExecutedPriceLeavesTheCheckUnableToRunRatherThanSilent() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today = positions(cash("1000000"), units("800000"));

    var findings = checker.check(TUK75, today, previous, THRESHOLD, Map.of());

    assertThat(findings).hasSize(1);
    var finding = findings.getFirst();
    assertThat(finding.checkType()).isEqualTo(NAV_FLOW_CONSISTENCY);
    assertThat(finding.severity()).isEqualTo(NOT_RUN);
    assertThat(finding.message()).contains("IE00A");
  }

  // SEB keeps a sold-out holding on the report for one more day, at quantity 0 but still priced,
  // and the line only disappears the day after that. A mark multiplies yesterday's quantity, so a
  // zero-quantity line cannot move the identity at any price - taking the whole fund's
  // reconciliation down over it blocks the check on a leg worth exactly nothing. This is the shape
  // that produced the 27.08.2026 alert for TUK75 and TUV100.
  @Test
  void aHoldingThatAlreadyLeftAtZeroQuantityDoesNotStopTheCheck() {
    var previous =
        positions(
            security("IE00A", "0", "17.32", "0"),
            security("IE00B", "10000", "100", "1000000"),
            units("1000000"));
    var today = positions(security("IE00B", "10000", "102", "1020000"), units("1000000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD, Map.of())).isEmpty();
  }

  // Selling part of a holding leaves it on the report, still priced, and the ootel report still
  // carries an executed price for the part that left. The report's own mark is the better one - it
  // covers the whole position - so the exit mark must lose to it. Marking the full opening quantity
  // at the execution price instead would value the part that stayed at the price the part that left
  // went out at, and invent a dealing cost over the whole line.
  @Test
  void aPartlySoldHoldingIsMarkedAtTheReportPriceRatherThanItsExecutionPrice() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today =
        positions(security("IE00A", "6000", "102", "612000"), cash("408000"), units("1000000"));

    var exitMarks =
        Map.of(
            "IE00A",
            new ExitMark(
                new BigDecimal("99"),
                new ExitMark.PublishedPrice(new BigDecimal("101"), LocalDate.parse("2026-08-25"))));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD, exitMarks)).isEmpty();
  }

  // SebFundPositionParser stores the report's "Total" row as AccountType.NAV, so every imported
  // day carries the net asset total alongside the very lines that sum to it. Counting both makes
  // opening net assets twice the truth, and a day where only prices moved starts warning.
  @Test
  void theReportsOwnTotalRowIsNotAddedToTheLinesItTotals() {
    var previous =
        positions(security("IE00A", "10000", "100", "1000000"), units("1000000"), total("1000000"));
    var today =
        positions(security("IE00A", "10000", "102", "1020000"), units("1000000"), total("1020000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD, Map.of())).isEmpty();
  }

  // quantitiesChanged is what tells the reader whether trading can explain the gap, and every
  // other case here leaves the holdings untouched, so only its false branch was ever asserted.
  @Test
  void aPurchaseWithNoMatchingCashMovementIsReportedAsHavingMovedQuantities() {
    var previous =
        positions(security("IE00A", "10000", "100", "1000000"), units("1000000"), total("1000000"));
    var today =
        positions(security("IE00A", "12000", "100", "1200000"), units("1000000"), total("1200000"));

    var findings = checker.check(TUK75, today, previous, THRESHOLD, Map.of());

    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().message())
        .contains("unexplained=200000.00")
        .contains("quantitiesChanged=true");
  }

  // The gate is a strict "less than", so a fraction sitting exactly on the threshold warns.
  @Test
  void aGapExactlyOnTheThresholdWarns() {
    var previous =
        positions(security("IE00A", "10000", "100", "1000000"), units("1000000"), total("1000000"));
    var today =
        positions(
            security("IE00A", "10000", "100", "1000000"),
            cash("1000"),
            units("1000000"),
            total("1001000"));

    var findings = checker.check(TUK75, today, previous, new BigDecimal("0.001"), Map.of());

    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().severity()).isEqualTo(WARNING);
  }

  // Dividing the gap by a fund that was empty yesterday says nothing, so there is nothing to check.
  @Test
  void aFundWithNothingInItYesterdayHasNoFractionToReport() {
    var previous = positions(units("1000000"), total("0"));
    var today =
        positions(security("IE00A", "10000", "100", "1000000"), units("1000000"), total("1000000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD, Map.of())).isEmpty();
  }

  // A threshold row that does not reach back to the nav date being checked used to throw out of
  // HealthCheckService, and the import job swallows that per date - so one missing parameter row
  // silently dropped every check for the date while the pipeline still reported success.
  @Test
  void aMissingThresholdSaysTheCheckCouldNotRunRatherThanBreakingTheImport() {
    var previous =
        positions(security("IE00A", "10000", "100", "1000000"), units("1000000"), total("1000000"));
    var today =
        positions(security("IE00A", "10000", "102", "1020000"), units("1000000"), total("1020000"));

    var findings = checker.check(TUK75, today, previous, null, Map.of());

    assertThat(findings).hasSize(1);
    var finding = findings.getFirst();
    assertThat(finding.checkType()).isEqualTo(NAV_FLOW_CONSISTENCY);
    assertThat(finding.severity()).isEqualTo(NOT_RUN);
    assertThat(finding.message()).contains("NAV_FLOW_CONSISTENCY_THRESHOLD");
  }

  // SebFundPositionParser defaults every row it does not recognise to SECURITY, so a renamed or
  // added report line arrives as a security with no ISIN: counted in net assets, invisible to the
  // mark-to-market loop, and its whole daily movement lands in the residual as a phantom breach.
  @Test
  void aValuedSecurityRowWithNoIsinCannotBeMarkedSoTheCheckCannotRun() {
    var previous =
        positions(
            security("IE00A", "10000", "100", "1000000"),
            valued(SECURITY, "Accrued interest", "5000"),
            units("1000000"));
    var today =
        positions(
            security("IE00A", "10000", "102", "1020000"),
            valued(SECURITY, "Accrued interest", "9000"),
            units("1000000"));

    var findings = checker.check(TUK75, today, previous, THRESHOLD, Map.of());

    assertThat(findings).hasSize(1);
    var finding = findings.getFirst();
    assertThat(finding.severity()).isEqualTo(NOT_RUN);
    assertThat(finding.message()).contains("Accrued interest");
  }

  // byIsin sums the quantities of two rows carrying one ISIN while pricesByIsin keeps whichever
  // price it saw first, so a report pricing the same instrument two ways valued the whole summed
  // holding at an arbitrary one of them. One instrument has one price on one day; two means the
  // report contradicts itself and there is nothing honest to mark against.
  @Test
  void theSameIsinPricedTwoWaysInOneReportStopsTheCheck() {
    var previous =
        positions(
            security("IE00A", "6000", "100", "600000"),
            security("IE00A", "4000", "99", "396000"),
            units("1000000"));
    var today = positions(security("IE00A", "10000", "102", "1020000"), units("1000000"));

    var findings = checker.check(TUK75, today, previous, THRESHOLD, Map.of());

    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().severity()).isEqualTo(NOT_RUN);
    assertThat(findings.getFirst().message()).contains("IE00A");
  }

  // Only yesterday's holdings are marked to market, so a contradiction on a line bought today
  // cannot reach the arithmetic and must not take the fund's reconciliation down with it.
  @Test
  void aPriceContradictionOnAHoldingBoughtTodayDoesNotStopTheCheck() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today =
        positions(
            security("IE00A", "10000", "102", "1020000"),
            security("IE00B", "600", "50", "30000"),
            security("IE00B", "400", "49", "19600"),
            cash("-49600"),
            units("1000000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD, Map.of())).isEmpty();
  }

  // The same holding split across two accounts at one price is ordinary, and summing its quantities
  // is exactly right - so the guard above must not fire on it.
  @Test
  void theSameIsinSplitAcrossTwoAccountsAtOnePriceReconciles() {
    var previous =
        positions(
            security("IE00A", "6000", "100", "600000"),
            security("IE00A", "4000", "100.00", "400000"),
            units("1000000"));
    var today =
        positions(
            security("IE00A", "6000", "102", "612000"),
            security("IE00A", "4000", "102", "408000"),
            units("1000000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD, Map.of())).isEmpty();
  }

  // A zero-valued row carries no movement to explain, so it must not be able to silence a fund.
  @Test
  void aSecurityRowWithNoIsinAndNoValueDoesNotStopTheCheck() {
    var previous =
        positions(
            security("IE00A", "10000", "100", "1000000"),
            valued(SECURITY, "Closed account", "0"),
            units("1000000"));
    var today =
        positions(
            security("IE00A", "10000", "102", "1020000"),
            valued(SECURITY, "Closed account", "0"),
            units("1000000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD, Map.of())).isEmpty();
  }

  // Today's missing units row is OutstandingUnitsChecker's finding to make. Yesterday's is
  // nobody's,
  // so returning quietly here left a partial previous-day report looking like a clean
  // reconciliation.
  @Test
  void aPreviousDayWithoutAUnitsRowSaysTheCheckCouldNotRun() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"));
    var today = positions(security("IE00A", "10000", "102", "1020000"), units("1000000"));

    var findings = checker.check(TUK75, today, previous, THRESHOLD, Map.of());

    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().severity()).isEqualTo(NOT_RUN);
    assertThat(findings.getFirst().message()).contains("units");
  }

  @Test
  void aFirstEverImportHasNothingToReconcileAgainst() {
    var today = positions(security("IE00A", "10000", "100", "1000000"), units("800000"));

    assertThat(checker.check(TUK75, today, List.of(), THRESHOLD, Map.of())).isEmpty();
  }

  @Test
  void aReportWithoutUnitsLeavesTheCheckSilent() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"));
    var today = positions(security("IE00A", "10000", "100", "1000000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD, Map.of())).isEmpty();
  }

  private List<FundPosition> positions(FundPosition... positions) {
    return new ArrayList<>(List.of(positions));
  }

  private FundPosition security(
      String isin, String quantity, String marketPrice, String marketValue) {
    return FundPosition.builder()
        .fund(TUK75)
        .accountType(SECURITY)
        .accountName(isin)
        .accountId(isin)
        .quantity(new BigDecimal(quantity))
        .marketPrice(new BigDecimal(marketPrice))
        .marketValue(new BigDecimal(marketValue))
        .build();
  }

  private FundPosition cash(String marketValue) {
    return valued(CASH, "Cash", marketValue);
  }

  private FundPosition total(String marketValue) {
    return valued(NAV, "Total", marketValue);
  }

  private FundPosition units(String quantity) {
    return FundPosition.builder()
        .fund(TUK75)
        .accountType(UNITS)
        .accountName("Outstanding units")
        .quantity(new BigDecimal(quantity))
        .build();
  }

  private FundPosition valued(AccountType accountType, String accountName, String marketValue) {
    return FundPosition.builder()
        .fund(TUK75)
        .accountType(accountType)
        .accountName(accountName)
        .marketValue(new BigDecimal(marketValue))
        .build();
  }
}
