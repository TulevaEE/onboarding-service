package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNSETTLED_TRADES;
import static ee.tuleva.onboarding.investment.check.health.PendingTradeChangesTest.report;
import static ee.tuleva.onboarding.investment.check.health.PendingTradeChangesTest.trade;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.transaction.PendingTrades;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UnsettledTradeCheckerTest {

  private static final LocalDate PREVIOUS = LocalDate.of(2026, 10, 19);
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 20);
  private static final BigDecimal AUM = new BigDecimal("1172664137.31");
  private static final BigDecimal UNITS = new BigDecimal("722432936.432");

  private final UnsettledTradeChecker checker = new UnsettledTradeChecker();

  @Test
  void passesWhenTheTradeRowsEqualThePendingReport() {
    var pending =
        report(
            NAV_DATE,
            List.of(
                trade(TUK75, "DLA2", "IE00B", BUY, "2925.38", "115536"),
                trade(TUK75, "DLA3", "IE00C", BUY, "74288.82", "1107572"),
                trade(TUK75, "DLA4", "IE00D", SELL, "10", "500.25")));

    var findings =
        checker.check(
            TUK75,
            List.of(position(RECEIVABLES, "Total receivables of unsettled transactions", "500.25")),
            List.of(position(LIABILITY, "Total payables of unsettled transactions", "-1223108.00")),
            pending,
            PendingTradeChanges.unavailable("not needed"),
            AUM,
            UNITS);

    assertThat(findings).isEmpty();
  }

  @Test
  void failsWhenATradeThatSettledIsStillInThePayables() {
    var settled = trade(TUK75, "DLA1", "IE00A", BUY, "601870", "3088796.84");
    var stillPending =
        List.of(
            trade(TUK75, "DLA2", "IE00B", BUY, "2925.38", "115536"),
            trade(TUK75, "DLA3", "IE00C", BUY, "74288.82", "1107572"));
    var previous = report(PREVIOUS, List.of(settled));
    var current = report(NAV_DATE, stillPending);

    var findings =
        checker.check(
            TUK75,
            List.of(),
            List.of(position(LIABILITY, "Total payables of unsettled transactions", "-4311904.84")),
            current,
            PendingTradeChanges.between(TUK75, previous, current),
            AUM,
            UNITS);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                UNSETTLED_TRADES,
                FAIL,
                "Trade payables 4311904.84, pending buys 1223108.00, diff 3088796.84"
                    + " = DLA1, settled 2026-10-20"));
  }

  @Test
  void namesNoCauseWhenNoSettledTradeAccountsForTheGap() {
    var current = report(NAV_DATE, List.of(trade(TUK75, "DLA2", "IE00B", BUY, "10", "1000")));

    var findings =
        checker.check(
            TUK75,
            List.of(),
            List.of(position(LIABILITY, "Total payables of unsettled transactions", "-5000")),
            current,
            PendingTradeChanges.between(TUK75, report(PREVIOUS, List.of()), current),
            AUM,
            UNITS);

    assertThat(findings)
        .extracting(HealthCheckFinding::message)
        .containsExactly("Trade payables 5000.00, pending buys 1000.00, diff 4000.00");
  }

  @Test
  void warnsWithoutBlockingWhenTheGapIsTooSmallToMoveTheNavPerUnit() {
    var findings =
        checker.check(
            TUK75,
            List.of(),
            List.of(position(LIABILITY, "Total payables of unsettled transactions", "-1000.50")),
            report(NAV_DATE, List.of(trade(TUK75, "DLA2", "IE00B", BUY, "10", "1000"))),
            PendingTradeChanges.unavailable("not needed"),
            AUM,
            UNITS);

    assertThat(findings).extracting(HealthCheckFinding::severity).containsExactly(WARNING);
  }

  @Test
  void failsWhenTheNavImpactCannotBeDetermined() {
    var findings =
        checker.check(
            TUK75,
            List.of(),
            List.of(position(LIABILITY, "Total payables of unsettled transactions", "-1000.50")),
            report(NAV_DATE, List.of(trade(TUK75, "DLA2", "IE00B", BUY, "10", "1000"))),
            PendingTradeChanges.unavailable("not needed"),
            AUM,
            null);

    assertThat(findings).extracting(HealthCheckFinding::severity).containsExactly(FAIL);
  }

  @Test
  void comparesTradeReceivablesWithPendingSells() {
    var findings =
        checker.check(
            TUK75,
            List.of(position(RECEIVABLES, "Total receivables of unsettled transactions", "0.00")),
            List.of(),
            report(NAV_DATE, List.of(trade(TUK75, "DLA4", "IE00D", SELL, "1000", "2500000"))),
            PendingTradeChanges.unavailable("not needed"),
            AUM,
            UNITS);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                UNSETTLED_TRADES,
                FAIL,
                "Trade receivables 0.00, pending sells 2500000.00, diff -2500000.00"));
  }

  @Test
  void leavesUnitReceivablesAndRedemptionPayablesOut() {
    var findings =
        checker.check(
            TUK75,
            List.of(position(RECEIVABLES, "Receivables of outstanding units", "192.78")),
            List.of(position(LIABILITY, "Payables of redeemed units", "-5000")),
            report(NAV_DATE, List.of()),
            PendingTradeChanges.unavailable("not needed"),
            AUM,
            UNITS);

    assertThat(findings).isEmpty();
  }

  @Test
  void countsOnlyTheFundsOwnPendingTrades() {
    var findings =
        checker.check(
            TUK75,
            List.of(),
            List.of(position(LIABILITY, "Total payables of unsettled transactions", "0.00")),
            report(NAV_DATE, List.of(trade(TUK00, "DLA5", "IE00E", BUY, "2270.74", "52520"))),
            PendingTradeChanges.unavailable("not needed"),
            AUM,
            UNITS);

    assertThat(findings).isEmpty();
  }

  @Test
  void saysItCouldNotRunWithoutThePendingReport() {
    var findings =
        checker.check(
            TUK75,
            List.of(),
            List.of(position(LIABILITY, "Total payables of unsettled transactions", "-1000")),
            new PendingTradeReport(NAV_DATE, null),
            PendingTradeChanges.unavailable("not needed"),
            AUM,
            UNITS);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                UNSETTLED_TRADES,
                NOT_RUN,
                "Not checked: no SEB pending transactions report as of 2026-10-20"));
  }

  @Test
  void saysItCouldNotRunOnAPendingReportWithUnusableRowsOfTheFund() {
    var findings =
        checker.check(
            TUK75,
            List.of(),
            List.of(),
            new PendingTradeReport(NAV_DATE, new PendingTrades(List.of(), Map.of(TUK75, 1), 0)),
            PendingTradeChanges.unavailable("not needed"),
            AUM,
            UNITS);

    assertThat(findings).extracting(HealthCheckFinding::severity).containsExactly(NOT_RUN);
  }

  private FundPosition position(AccountType type, String name, String marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(type)
        .accountName(name)
        .marketValue(new BigDecimal(marketValue))
        .build();
  }
}
