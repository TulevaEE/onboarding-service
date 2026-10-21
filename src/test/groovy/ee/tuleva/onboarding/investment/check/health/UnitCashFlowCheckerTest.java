package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNIT_CASH_FLOW;
import static ee.tuleva.onboarding.investment.check.health.PendingTradeChangesTest.report;
import static ee.tuleva.onboarding.investment.check.health.PendingTradeChangesTest.trade;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.NAV;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.position.AccountType.UNITS;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class UnitCashFlowCheckerTest {

  private static final LocalDate PREVIOUS = LocalDate.of(2026, 10, 19);
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 20);
  private static final BigDecimal THRESHOLD = new BigDecimal("0.0005");

  private final UnitCashFlowChecker checker = new UnitCashFlowChecker();

  @Test
  void passesWhenTheUnitChangeMatchesTheCashThatMovedForUnits() {
    var previous =
        report(
            PREVIOUS,
            List.of(
                trade(TUK75, "DLA1", "IE00A", BUY, "263673", "2730597.59"),
                trade(TUK75, "DLA2", "IE00B", BUY, "601870", "3088796.84")));
    var current = report(NAV_DATE, List.of());

    var findings =
        checker.check(
            TUK75,
            snapshot(
                TUK75, NAV_DATE, "2613401.69", "722432936.432", "1172664137.31", "192.78", null),
            snapshot(TUK75, PREVIOUS, "8404961.92", "722416023.209", "1175396068.69", "0", null),
            PendingTradeChanges.between(TUK75, previous, current),
            THRESHOLD);

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenTheUnitCountMovesWithoutTheCashToMatch() {
    var findings =
        checker.check(
            TUK00,
            snapshot(TUK00, NAV_DATE, "267838.53", "18895375.177", "11421366.17", "0", null),
            snapshot(TUK00, PREVIOUS, "267382.61", "18913450.439", "11444389.60", "0", null),
            noTradeChanges(TUK00),
            THRESHOLD);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK00,
                UNIT_CASH_FLOW,
                WARNING,
                "Units -18075.262 but unit cash +455.92 EUR; cash implies 18914203.910 units,"
                    + " report 18895375.177"));
  }

  @Test
  void countsAPayableForRedeemedUnitsAsCashLeavingForUnits() {
    var findings =
        checker.check(
            TUK00,
            snapshot(TUK00, NAV_DATE, "100000", "990000", "990000", "0", "-10000"),
            snapshot(TUK00, PREVIOUS, "100000", "1000000", "1000000", "0", null),
            noTradeChanges(TUK00),
            THRESHOLD);

    assertThat(findings).isEmpty();
  }

  @Test
  void leavesReceivablesThatAreNotForUnitsOutOfTheUnitCash() {
    var today =
        new ArrayList<>(snapshot(TUK00, NAV_DATE, "100000", "1000000", "1000000", "0", null));
    today.add(
        position(TUK00, NAV_DATE, RECEIVABLES, "Dividend receivable", "IE0005032192", "20000"));

    var findings =
        checker.check(
            TUK00,
            today,
            snapshot(TUK00, PREVIOUS, "100000", "1000000", "1000000", "0", null),
            noTradeChanges(TUK00),
            THRESHOLD);

    assertThat(findings).isEmpty();
  }

  @Test
  void passesWhenTheGapStaysUnderTheThreshold() {
    var findings =
        checker.check(
            TUK00,
            snapshot(TUK00, NAV_DATE, "100400", "1000000", "1000000", "0", null),
            snapshot(TUK00, PREVIOUS, "100000", "1000000", "1000000", "0", null),
            noTradeChanges(TUK00),
            THRESHOLD);

    assertThat(findings).isEmpty();
  }

  @Test
  void hasNothingToCompareOnTheFundsFirstReport() {
    var findings =
        checker.check(
            TUK00,
            snapshot(TUK00, NAV_DATE, "100000", "1000000", "1000000", "0", null),
            List.of(),
            noTradeChanges(TUK00),
            THRESHOLD);

    assertThat(findings).isEmpty();
  }

  @Test
  void saysItCouldNotRunWithoutAThreshold() {
    var findings =
        checker.check(
            TUK00,
            snapshot(TUK00, NAV_DATE, "100000", "1000000", "1000000", "0", null),
            snapshot(TUK00, PREVIOUS, "100000", "1000000", "1000000", "0", null),
            noTradeChanges(TUK00),
            null);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK00,
                UNIT_CASH_FLOW,
                NOT_RUN,
                "Not checked: UNIT_CASH_FLOW_THRESHOLD not configured"));
  }

  @Test
  void saysItCouldNotRunWithoutBothPendingReports() {
    var findings =
        checker.check(
            TUK00,
            snapshot(TUK00, NAV_DATE, "100000", "1000000", "1000000", "0", null),
            snapshot(TUK00, PREVIOUS, "100000", "1000000", "1000000", "0", null),
            PendingTradeChanges.unavailable("no SEB pending transactions report as of 2026-10-19"),
            THRESHOLD);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK00,
                UNIT_CASH_FLOW,
                NOT_RUN,
                "Not checked: no SEB pending transactions report as of 2026-10-19"));
  }

  @Test
  void saysItCouldNotRunWhenTheReportHasNoUnitCount() {
    var withoutUnits =
        snapshot(TUK00, NAV_DATE, "100000", "1000000", "1000000", "0", null).stream()
            .filter(position -> position.getAccountType() != UNITS)
            .toList();

    var findings =
        checker.check(
            TUK00,
            withoutUnits,
            snapshot(TUK00, PREVIOUS, "100000", "1000000", "1000000", "0", null),
            noTradeChanges(TUK00),
            THRESHOLD);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK00,
                UNIT_CASH_FLOW,
                NOT_RUN,
                "Not checked: the position report as of 2026-10-20 has no unit count or cash"));
  }

  private PendingTradeChanges noTradeChanges(TulevaFund fund) {
    return PendingTradeChanges.between(
        fund, report(PREVIOUS, List.of()), report(NAV_DATE, List.of()));
  }

  private List<FundPosition> snapshot(
      TulevaFund fund,
      LocalDate date,
      String cash,
      String units,
      String total,
      String unitReceivables,
      @Nullable String redemptionPayables) {
    var positions = new ArrayList<FundPosition>();
    positions.add(position(fund, date, CASH, "Cash account in SEB Pank", null, cash));
    positions.add(
        FundPosition.builder()
            .navDate(date)
            .fund(fund)
            .accountType(UNITS)
            .accountName("Total outstanding units:")
            .accountId(fund.getIsin())
            .quantity(new BigDecimal(units))
            .build());
    positions.add(position(fund, date, NAV, "Total", null, total));
    positions.add(
        position(
            fund,
            date,
            RECEIVABLES,
            "Receivables of outstanding units",
            fund.getIsin(),
            unitReceivables));
    positions.add(
        position(
            fund, date, RECEIVABLES, "Total receivables of unsettled transactions", null, "0"));
    positions.add(
        position(fund, date, LIABILITY, "Total payables of unsettled transactions", null, "-999"));
    if (redemptionPayables != null) {
      positions.add(
          position(
              fund,
              date,
              LIABILITY,
              "Payables of redeemed units",
              fund.getIsin(),
              redemptionPayables));
    }
    return positions;
  }

  private FundPosition position(
      TulevaFund fund,
      LocalDate date,
      AccountType type,
      String name,
      @Nullable String accountId,
      String marketValue) {
    return FundPosition.builder()
        .navDate(date)
        .fund(fund)
        .accountType(type)
        .accountName(name)
        .accountId(accountId)
        .marketValue(new BigDecimal(marketValue))
        .build();
  }
}
