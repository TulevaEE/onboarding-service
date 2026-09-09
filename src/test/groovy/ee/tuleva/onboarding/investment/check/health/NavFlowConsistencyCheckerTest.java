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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavFlowConsistencyCheckerTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("0.001");

  private final NavFlowConsistencyChecker checker = new NavFlowConsistencyChecker();

  @Test
  void aDayWhereOnlyPricesMovedReconciles() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today = positions(security("IE00A", "10000", "102", "1020000"), units("1000000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD)).isEmpty();
  }

  @Test
  void aSubscriptionSettledAtNavReconciles() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today =
        positions(security("IE00A", "10000", "102", "1020000"), cash("102000"), units("1100000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD)).isEmpty();
  }

  @Test
  void aRedemptionWhosePayoutWasNeverBookedWarns() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today = positions(security("IE00A", "10000", "100", "1000000"), units("800000"));

    var findings = checker.check(TUK75, today, previous, THRESHOLD);

    assertThat(findings).hasSize(1);
    var finding = findings.getFirst();
    assertThat(finding.fund()).isEqualTo(TUK75);
    assertThat(finding.checkType()).isEqualTo(NAV_FLOW_CONSISTENCY);
    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.message())
        .contains("unexplained=250000.00")
        .contains("quantitiesChanged=false");
  }

  // A holding sold out during the day leaves no price to mark it against, so the day's market
  // P&L is genuinely unknowable from the position report - and a settlement day is exactly when
  // this check matters, so it has to say it could not run rather than return quietly.
  @Test
  void aHoldingSoldOutDuringTheDayLeavesTheCheckUnableToRunRatherThanSilent() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"), units("1000000"));
    var today = positions(cash("1000000"), units("800000"));

    var findings = checker.check(TUK75, today, previous, THRESHOLD);

    assertThat(findings).hasSize(1);
    var finding = findings.getFirst();
    assertThat(finding.checkType()).isEqualTo(NAV_FLOW_CONSISTENCY);
    assertThat(finding.severity()).isEqualTo(NOT_RUN);
    assertThat(finding.message()).contains("IE00A");
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

    assertThat(checker.check(TUK75, today, previous, THRESHOLD)).isEmpty();
  }

  // quantitiesChanged is what tells the reader whether trading can explain the gap, and every
  // other case here leaves the holdings untouched, so only its false branch was ever asserted.
  @Test
  void aPurchaseWithNoMatchingCashMovementIsReportedAsHavingMovedQuantities() {
    var previous =
        positions(security("IE00A", "10000", "100", "1000000"), units("1000000"), total("1000000"));
    var today =
        positions(security("IE00A", "12000", "100", "1200000"), units("1000000"), total("1200000"));

    var findings = checker.check(TUK75, today, previous, THRESHOLD);

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

    var findings = checker.check(TUK75, today, previous, new BigDecimal("0.001"));

    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().severity()).isEqualTo(WARNING);
  }

  // Dividing the gap by a fund that was empty yesterday says nothing, so there is nothing to check.
  @Test
  void aFundWithNothingInItYesterdayHasNoFractionToReport() {
    var previous = positions(units("1000000"), total("0"));
    var today =
        positions(security("IE00A", "10000", "100", "1000000"), units("1000000"), total("1000000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD)).isEmpty();
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

    var findings = checker.check(TUK75, today, previous, null);

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

    var findings = checker.check(TUK75, today, previous, THRESHOLD);

    assertThat(findings).hasSize(1);
    var finding = findings.getFirst();
    assertThat(finding.severity()).isEqualTo(NOT_RUN);
    assertThat(finding.message()).contains("Accrued interest");
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

    assertThat(checker.check(TUK75, today, previous, THRESHOLD)).isEmpty();
  }

  // Today's missing units row is OutstandingUnitsChecker's finding to make. Yesterday's is
  // nobody's,
  // so returning quietly here left a partial previous-day report looking like a clean
  // reconciliation.
  @Test
  void aPreviousDayWithoutAUnitsRowSaysTheCheckCouldNotRun() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"));
    var today = positions(security("IE00A", "10000", "102", "1020000"), units("1000000"));

    var findings = checker.check(TUK75, today, previous, THRESHOLD);

    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().severity()).isEqualTo(NOT_RUN);
    assertThat(findings.getFirst().message()).contains("units");
  }

  @Test
  void aFirstEverImportHasNothingToReconcileAgainst() {
    var today = positions(security("IE00A", "10000", "100", "1000000"), units("800000"));

    assertThat(checker.check(TUK75, today, List.of(), THRESHOLD)).isEmpty();
  }

  @Test
  void aReportWithoutUnitsLeavesTheCheckSilent() {
    var previous = positions(security("IE00A", "10000", "100", "1000000"));
    var today = positions(security("IE00A", "10000", "100", "1000000"));

    assertThat(checker.check(TUK75, today, previous, THRESHOLD)).isEmpty();
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
