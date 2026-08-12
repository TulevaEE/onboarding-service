package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.QUANTITY_CHANGE;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuantityChangeCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);
  private final QuantityChangeChecker checker = new QuantityChangeChecker();

  @Test
  void noFindingsWhenQuantityUnchanged() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void noFindingsWhenNoPreviousDayData() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, List.of(), Map.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenANewHoldingAppearsWithoutAPurchase() {
    var today =
        List.of(
            security("IE0009FT4LX4", new BigDecimal("1000")),
            security("IE000QWCYQT0", new BigDecimal("500")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.severity()).isEqualTo(WARNING);
              assertThat(finding.message()).contains("IE000QWCYQT0", "500", "previous 0");
            });
  }

  @Test
  void noFindingsWhenANewHoldingWasBought() {
    var today =
        List.of(
            security("IE0009FT4LX4", new BigDecimal("1000")),
            security("IE000QWCYQT0", new BigDecimal("500")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var traded = Map.of("IE000QWCYQT0", new TradedQuantity(new BigDecimal("500"), ZERO));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenQuantityIncreasedWithoutAnyTransaction() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1500")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.fund()).isEqualTo(TUK75);
              assertThat(finding.checkType()).isEqualTo(QUANTITY_CHANGE);
              assertThat(finding.severity()).isEqualTo(WARNING);
              assertThat(finding.message()).contains("IE0009FT4LX4", "500", "1000", "1500");
            });
  }

  @Test
  void warnsWhenQuantityDecreasedWithoutAnyTransaction() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("900")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void noFindingsWhenIncreaseIsCoveredByExecutedBuys() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1500")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var traded = Map.of("IE0009FT4LX4", new TradedQuantity(new BigDecimal("500"), ZERO));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings).isEmpty();
  }

  @Test
  void noFindingsWhenDecreaseIsCoveredByExecutedSells() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("400")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var traded = Map.of("IE0009FT4LX4", new TradedQuantity(ZERO, new BigDecimal("600")));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings).isEmpty();
  }

  @Test
  void noFindingsWhenABuyAndASellNetToTheReportedChange() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1100")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var traded =
        Map.of("IE0009FT4LX4", new TradedQuantity(new BigDecimal("1000"), new BigDecimal("900")));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenTheChangeIgnoresASettledSaleAndOnlyMatchesTheBuys() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("2000")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var traded =
        Map.of("IE0009FT4LX4", new TradedQuantity(new BigDecimal("1000"), new BigDecimal("900")));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.severity()).isEqualTo(WARNING);
              assertThat(f.message()).contains("IE0009FT4LX4", "1000", "2000", "100");
            });
  }

  @Test
  void warnsWhenLessSettledThanWeTraded() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1200")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var traded = Map.of("IE0009FT4LX4", new TradedQuantity(new BigDecimal("500"), ZERO));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void warnsWhenAnExecutedSaleDidNotMoveThePositionAtAll() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var traded = Map.of("IE0009FT4LX4", new TradedQuantity(ZERO, new BigDecimal("600")));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void warnsWhenIncreaseExceedsExecutedBuys() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("2000")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var traded = Map.of("IE0009FT4LX4", new TradedQuantity(new BigDecimal("500"), ZERO));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.message()).contains("IE0009FT4LX4", "1000", "500"));
  }

  @Test
  void warnsWhenIncreaseIsOnlyCoveredByExecutedSells() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1500")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var traded = Map.of("IE0009FT4LX4", new TradedQuantity(ZERO, new BigDecimal("5000")));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void noFindingsWhenChangeIsWithinRoundingTolerance() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1000.004")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenTheChangeExceedsTheExecutedQuantityByASmallMargin() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1550")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var traded = Map.of("IE0009FT4LX4", new TradedQuantity(new BigDecimal("500"), ZERO));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void warnsOnSubUnitChangeWhenWeTradedNothing() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1000.5")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void sumsRepeatedIsinRowsOnBothSidesInsteadOfFailing() {
    var today =
        List.of(
            security("IE0009FT4LX4", new BigDecimal("600")),
            security("IE0009FT4LX4", new BigDecimal("400")));
    var previous =
        List.of(
            security("IE0009FT4LX4", new BigDecimal("700")),
            security("IE0009FT4LX4", new BigDecimal("300")));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void reportsRepeatedIsinRowsOnceWithTheAggregatedChange() {
    var today =
        List.of(
            security("IE0009FT4LX4", new BigDecimal("600")),
            security("IE0009FT4LX4", new BigDecimal("900")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.message()).contains("IE0009FT4LX4", "500", "1500"));
  }

  @Test
  void warnsWhenAHoldingDisappearsFromTheReportWithoutASale() {
    var today = List.of(security("IE00BFG1TM61", new BigDecimal("2000")));
    var previous =
        List.of(
            security("IE00BFG1TM61", new BigDecimal("2000")),
            security("IE0009FT4LX4", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.severity()).isEqualTo(WARNING);
              assertThat(f.message()).contains("IE0009FT4LX4", "-1000", "current 0");
            });
  }

  @Test
  void noFindingsWhenADisappearedHoldingWasFullySold() {
    var today = List.of(security("IE00BFG1TM61", new BigDecimal("2000")));
    var previous =
        List.of(
            security("IE00BFG1TM61", new BigDecimal("2000")),
            security("IE0009FT4LX4", new BigDecimal("1000")));
    var traded = Map.of("IE0009FT4LX4", new TradedQuantity(ZERO, new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsForEveryHoldingWhenTheReportHasNoSecuritiesAtAll() {
    var previous =
        List.of(
            security("IE0009FT4LX4", new BigDecimal("1000")),
            security("IE00BFG1TM61", new BigDecimal("2000")));

    var findings = checker.check(TUK75, List.of(), previous, Map.of());

    assertThat(findings).hasSize(2);
    assertThat(findings).allMatch(f -> f.severity() == WARNING);
    assertThat(findings.get(0).message()).contains("IE0009FT4LX4", "-1000", "current 0");
    assertThat(findings.get(1).message()).contains("IE00BFG1TM61", "-2000", "current 0");
  }

  @Test
  void warnsWhenChangeDwarfsATinyExecutedQuantity() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("11.10")));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("10")));
    var traded = Map.of("IE0009FT4LX4", new TradedQuantity(new BigDecimal("0.1"), ZERO));

    var findings = checker.check(TUK75, today, previous, traded);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void skipsPositionsWithoutIsinOrQuantity() {
    var today =
        List.of(
            security(null, new BigDecimal("1500")), security("IE0009FT4LX4", (BigDecimal) null));
    var previous = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void skipsIsinsWhoseOnlyPreviousRowCarriedNoQuantity() {
    var today = List.of(security("IE0009FT4LX4", new BigDecimal("1000")));
    var previous = List.of(security("IE0009FT4LX4", (BigDecimal) null));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void reportsEachUnexplainedIsinSortedByIsin() {
    var today =
        List.of(
            security("IE00BFG1TM61", new BigDecimal("2000")),
            security("IE0009FT4LX4", new BigDecimal("1500")));
    var previous =
        List.of(
            security("IE00BFG1TM61", new BigDecimal("1000")),
            security("IE0009FT4LX4", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, Map.of());

    assertThat(findings).hasSize(2);
    assertThat(findings.get(0).message()).contains("IE0009FT4LX4");
    assertThat(findings.get(1).message()).contains("IE00BFG1TM61");
  }

  private FundPosition security(String isin, BigDecimal quantity) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(SECURITY)
        .accountName("iShares Core MSCI World")
        .accountId(isin)
        .quantity(quantity)
        .build();
  }
}
