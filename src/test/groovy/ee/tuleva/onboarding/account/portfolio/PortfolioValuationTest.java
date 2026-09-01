package ee.tuleva.onboarding.account.portfolio;

import static ee.tuleva.onboarding.account.portfolio.PortfolioGroup.SAVINGS_FUND;
import static ee.tuleva.onboarding.account.portfolio.PortfolioGroup.SECOND_PILLAR;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION;
import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.CashFlow.Type.SUBTRACTION;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.epis.CashFlow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PortfolioValuationTest {

  private static final String TKF = "EE0000003283";
  private static final String PILLAR_2 = "EE3600109435";

  private static final Map<String, PortfolioGroup> GROUPS =
      Map.of(TKF, SAVINGS_FUND, PILLAR_2, SECOND_PILLAR);

  private static final Map<LocalDate, BigDecimal> TKF_NAVS =
      Map.of(
          LocalDate.parse("2025-01-01"), new BigDecimal("10"),
          LocalDate.parse("2025-06-30"), new BigDecimal("11"),
          LocalDate.parse("2025-12-31"), new BigDecimal("12"));

  private static final Map<LocalDate, BigDecimal> PILLAR_2_NAVS =
      Map.of(
          LocalDate.parse("2025-01-01"), new BigDecimal("2"),
          LocalDate.parse("2025-06-30"), new BigDecimal("2.5"),
          LocalDate.parse("2025-12-31"), new BigDecimal("3"));

  private static final List<Transaction> HISTORY =
      List.of(
          buy(TKF, "2025-01-01T10:00:00Z", 100, "10"),
          buy(TKF, "2025-06-30T10:00:00Z", 50, "11"),
          buy(PILLAR_2, "2025-01-01T10:00:00Z", 200, "2"));

  private static final Map<String, Map<LocalDate, BigDecimal>> NAV_HISTORY =
      Map.of(TKF, TKF_NAVS, PILLAR_2, PILLAR_2_NAVS);

  private static Transaction transaction(
      String isin, String time, int units, String nav, CashFlow.Type type) {
    return transaction(isin, time, time, units, nav, type);
  }

  private static Transaction transaction(
      String isin, String time, String priceTime, int units, String nav, CashFlow.Type type) {
    BigDecimal unitCount = BigDecimal.valueOf(units);
    BigDecimal navPerUnit = new BigDecimal(nav);
    return Transaction.builder()
        .id(UUID.nameUUIDFromBytes((isin + time + type).getBytes()))
        .amount(unitCount.multiply(navPerUnit))
        .currency(EUR)
        .time(Instant.parse(time))
        .priceTime(Instant.parse(priceTime))
        .isin(isin)
        .type(type)
        .units(unitCount)
        .nav(navPerUnit)
        .build();
  }

  private static Transaction buy(String isin, String time, int units, String nav) {
    return transaction(isin, time, units, nav, CONTRIBUTION_CASH);
  }

  private static Transaction buy(
      String isin, String time, String priceTime, int units, String nav) {
    return transaction(isin, time, priceTime, units, nav, CONTRIBUTION_CASH);
  }

  private static Transaction sell(String isin, String time, int units, String nav) {
    return transaction(isin, time, units, nav, SUBTRACTION);
  }

  private static Transaction contributionWithoutUnits(String isin, String time, String amount) {
    return Transaction.builder()
        .id(UUID.nameUUIDFromBytes((isin + time + CONTRIBUTION).getBytes()))
        .amount(new BigDecimal(amount))
        .currency(EUR)
        .time(Instant.parse(time))
        .isin(isin)
        .type(CONTRIBUTION)
        .build();
  }

  private static List<Transaction> alsoHolding(Transaction transaction) {
    return Stream.concat(HISTORY.stream(), Stream.of(transaction)).toList();
  }

  private static PortfolioValuation valuation(
      List<Transaction> transactions, Map<String, Map<LocalDate, BigDecimal>> navHistory) {
    return new PortfolioValuation(transactions, GROUPS, navHistory);
  }

  @Test
  void usesTheLatestPublishedPriceOnOrBeforeTheDate() {
    PortfolioValuation valuation = valuation(HISTORY, NAV_HISTORY);

    assertThat(valuation.navAt(TKF, LocalDate.parse("2025-06-30"))).isEqualByComparingTo("11");
    assertThat(valuation.navAt(TKF, LocalDate.parse("2025-09-15"))).isEqualByComparingTo("11");
    assertThat(valuation.navAt(TKF, LocalDate.parse("2024-12-31"))).isNull();
  }

  @Test
  void countsUnitsHeldOnADateNetOfRedemptions() {
    PortfolioValuation valuation = valuation(HISTORY, NAV_HISTORY);

    assertThat(valuation.unitsAt(TKF, LocalDate.parse("2025-01-01"))).isEqualByComparingTo("100");
    assertThat(valuation.unitsAt(TKF, LocalDate.parse("2025-06-30"))).isEqualByComparingTo("150");

    List<Transaction> withRedemption = alsoHolding(sell(TKF, "2025-07-01T10:00:00Z", 30, "11"));

    assertThat(valuation(withRedemption, NAV_HISTORY).unitsAt(TKF, LocalDate.parse("2025-07-01")))
        .isEqualByComparingTo("120");
  }

  @Test
  void valuesADateEvenWhenNothingWasTransactedThatDay() {
    assertThat(valuation(HISTORY, NAV_HISTORY).valueAt(Set.of(TKF), LocalDate.parse("2025-09-15")))
        .isEqualByComparingTo("1650.00");
  }

  @Test
  void addsUpTheSelectedFundsOnly() {
    PortfolioValuation valuation = valuation(HISTORY, NAV_HISTORY);
    LocalDate endOfYear = LocalDate.parse("2025-12-31");

    assertThat(valuation.valueAt(Set.of(TKF), endOfYear)).isEqualByComparingTo("1800.00");
    assertThat(valuation.valueAt(Set.of(PILLAR_2), endOfYear)).isEqualByComparingTo("600.00");
    assertThat(valuation.valueAt(Set.of(TKF, PILLAR_2), endOfYear)).isEqualByComparingTo("2400.00");
  }

  @Test
  void reportsTheGainEarnedBetweenTwoChosenDates() {
    Portfolio.GroupSummary summary =
        valuation(HISTORY, NAV_HISTORY)
            .summaryOf(SAVINGS_FUND, LocalDate.parse("2025-06-30"), LocalDate.parse("2025-12-31"));

    assertThat(summary.startValue()).isEqualByComparingTo("1000.00");
    assertThat(summary.endValue()).isEqualByComparingTo("1800.00");
    assertThat(summary.contributions()).isEqualByComparingTo("550.00");
    assertThat(summary.withdrawals()).isEqualByComparingTo("0.00");
    assertThat(summary.gain()).isEqualByComparingTo("250.00");
  }

  @Test
  void measuresGrowthOverAPeriodWithNoCashFlows() {
    Portfolio.GroupSummary summary =
        valuation(HISTORY, NAV_HISTORY)
            .summaryOf(SAVINGS_FUND, LocalDate.parse("2025-07-01"), LocalDate.parse("2025-12-31"));

    assertThat(summary.startValue()).isEqualByComparingTo("1650.00");
    assertThat(summary.endValue()).isEqualByComparingTo("1800.00");
    assertThat(summary.contributions()).isEqualByComparingTo("0.00");
    assertThat(summary.gain()).isEqualByComparingTo("150.00");
    assertThat(summary.gainPercentage()).isEqualByComparingTo("9.09");
  }

  @Test
  void buildsASeriesFromTheDaysAPriceWasPublishedInThePeriod() {
    List<Portfolio.ValuePoint> series =
        valuation(HISTORY, Map.of(TKF, TKF_NAVS))
            .series(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));

    assertThat(series.stream().map(Portfolio.ValuePoint::date))
        .containsExactly(
            LocalDate.parse("2025-01-01"),
            LocalDate.parse("2025-06-30"),
            LocalDate.parse("2025-12-31"));
    assertThat(series.getLast().values().get(SAVINGS_FUND)).isEqualByComparingTo("1800.00");
  }

  @Test
  void reportsNoEndValueWhenTheHeldFundHasNoPriceHistory() {
    Portfolio.GroupSummary summary =
        valuation(HISTORY, Map.of())
            .summaryOf(SAVINGS_FUND, LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));

    assertThat(summary.endValue()).isNull();
    assertThat(summary.gain()).isNull();
    assertThat(summary.gainPercentage()).isNull();
    assertThat(summary.startValue()).isEqualByComparingTo("0.00");
    assertThat(summary.contributions()).isEqualByComparingTo("1550.00");
    assertThat(summary.withdrawals()).isEqualByComparingTo("0.00");
  }

  @Test
  void reportsNoStartValueWhenThePriceHistoryBeginsAfterThePeriodOpens() {
    Map<String, Map<LocalDate, BigDecimal>> lateNavs =
        Map.of(TKF, Map.of(LocalDate.parse("2025-12-31"), new BigDecimal("12")));

    Portfolio.GroupSummary summary =
        valuation(HISTORY, lateNavs)
            .summaryOf(SAVINGS_FUND, LocalDate.parse("2025-06-30"), LocalDate.parse("2025-12-31"));

    assertThat(summary.startValue()).isNull();
    assertThat(summary.gain()).isNull();
    assertThat(summary.gainPercentage()).isNull();
    assertThat(summary.endValue()).isEqualByComparingTo("1800.00");
    assertThat(summary.contributions()).isEqualByComparingTo("550.00");
    assertThat(summary.withdrawals()).isEqualByComparingTo("0.00");
  }

  @Test
  void reportsNoValueWhileAHeldFundHasNoPublishedPrice() {
    Map<String, Map<LocalDate, BigDecimal>> lateNavs =
        Map.of(TKF, Map.of(LocalDate.parse("2025-06-30"), new BigDecimal("11")));
    PortfolioValuation valuation = valuation(HISTORY, lateNavs);

    assertThat(valuation.valueAt(Set.of(TKF), LocalDate.parse("2025-01-15"))).isNull();
    assertThat(valuation.valueAt(Set.of(TKF), LocalDate.parse("2025-06-30")))
        .isEqualByComparingTo("1650.00");
  }

  @Test
  void ignoresFundsThatAreNotHeldRatherThanBlankingTheWholeTotal() {
    assertThat(
            valuation(HISTORY, Map.of(TKF, TKF_NAVS))
                .valueAt(Set.of(TKF, "EE_NEVER_HELD"), LocalDate.parse("2025-12-31")))
        .isEqualByComparingTo("1800.00");
  }

  @Test
  void leavesAGroupUnvaluedOnDatesItHasNoPublishedPrice() {
    Map<String, Map<LocalDate, BigDecimal>> lateNavs =
        Map.of(
            TKF,
            Map.of(
                LocalDate.parse("2025-06-30"), new BigDecimal("11"),
                LocalDate.parse("2025-12-31"), new BigDecimal("12")),
            PILLAR_2,
            Map.of(LocalDate.parse("2025-01-01"), new BigDecimal("2")));

    List<Portfolio.ValuePoint> series =
        valuation(HISTORY, lateNavs)
            .series(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));

    Portfolio.ValuePoint firstPoint = series.getFirst();
    assertThat(firstPoint.date()).isEqualTo(LocalDate.parse("2025-01-01"));
    assertThat(firstPoint.values().get(SAVINGS_FUND)).isNull();
    assertThat(firstPoint.values().get(SECOND_PILLAR)).isEqualByComparingTo("400.00");
  }

  @Test
  void valuesEveryGroupOnTheSameDates() {
    List<Portfolio.ValuePoint> series =
        valuation(HISTORY, NAV_HISTORY)
            .series(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));

    Portfolio.ValuePoint last = series.getLast();
    assertThat(last.date()).isEqualTo(LocalDate.parse("2025-12-31"));
    assertThat(last.values().get(SAVINGS_FUND)).isEqualByComparingTo("1800.00");
    assertThat(last.values().get(SECOND_PILLAR)).isEqualByComparingTo("600.00");
  }

  @Test
  void reportsOnlyTheGroupsThePersonHolds() {
    assertThat(
            valuation(List.of(buy(TKF, "2025-01-01T10:00:00Z", 100, "10")), NAV_HISTORY)
                .heldGroups())
        .containsExactly(SAVINGS_FUND);
  }

  @Test
  void treatsEpisContributionsAsAcquisitions() {
    List<Transaction> episContribution =
        List.of(transaction(PILLAR_2, "2025-01-01T10:00:00Z", 200, "2", CONTRIBUTION));

    PortfolioValuation valuation = valuation(episContribution, NAV_HISTORY);

    assertThat(valuation.unitsAt(PILLAR_2, LocalDate.parse("2025-01-01")))
        .isEqualByComparingTo("200");

    Portfolio.GroupSummary summary =
        valuation.summaryOf(
            SECOND_PILLAR, LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));

    assertThat(summary.contributions()).isEqualByComparingTo("400.00");
    assertThat(summary.withdrawals()).isEqualByComparingTo("0.00");
    assertThat(summary.endValue()).isEqualByComparingTo("600.00");
  }

  @Test
  void datesUnitsAndCashFlowsByTheDayTheUnitsWerePricedNotTheDayTheyWereBooked() {
    List<Transaction> bookedBeforePricing =
        List.of(buy(TKF, "2025-06-29T10:00:00Z", "2025-07-01T00:00:00Z", 100, "10"));
    Map<String, Map<LocalDate, BigDecimal>> navs =
        Map.of(
            TKF,
            Map.of(
                LocalDate.parse("2025-06-30"), new BigDecimal("10"),
                LocalDate.parse("2025-07-01"), new BigDecimal("11")));

    PortfolioValuation valuation = valuation(bookedBeforePricing, navs);

    assertThat(valuation.unitsAt(TKF, LocalDate.parse("2025-06-30"))).isEqualByComparingTo("0");
    assertThat(valuation.unitsAt(TKF, LocalDate.parse("2025-07-01"))).isEqualByComparingTo("100");

    Portfolio.GroupSummary summary =
        valuation.summaryOf(
            SAVINGS_FUND, LocalDate.parse("2025-07-01"), LocalDate.parse("2025-07-01"));

    assertThat(summary.startValue()).isEqualByComparingTo("0.00");
    assertThat(summary.contributions()).isEqualByComparingTo("1000.00");
    assertThat(summary.endValue()).isEqualByComparingTo("1100.00");
    assertThat(summary.gain()).isEqualByComparingTo("100.00");
  }

  @Test
  void leavesAGroupUnvaluedWhenOneOfItsHoldingsHasATransactionWithoutUnits() {
    PortfolioValuation valuation =
        valuation(
            alsoHolding(contributionWithoutUnits(PILLAR_2, "2025-08-01T10:00:00Z", "40.00")),
            NAV_HISTORY);

    Portfolio.GroupSummary summary =
        valuation.summaryOf(
            SECOND_PILLAR, LocalDate.parse("2025-06-30"), LocalDate.parse("2025-12-31"));

    assertThat(summary.startValue()).isNull();
    assertThat(summary.endValue()).isNull();
    assertThat(summary.gain()).isNull();
    assertThat(summary.gainPercentage()).isNull();
    assertThat(summary.contributions()).isEqualByComparingTo("40.00");
    assertThat(summary.withdrawals()).isEqualByComparingTo("0.00");

    List<Portfolio.ValuePoint> series =
        valuation.series(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"));

    assertThat(series).allSatisfy(point -> assertThat(point.values().get(SECOND_PILLAR)).isNull());
    assertThat(series.getLast().values().get(SAVINGS_FUND)).isEqualByComparingTo("1800.00");
  }

  @Test
  void doesNotSubtractAFromDateDepositTwice() {
    List<Transaction> firstDay = List.of(buy(TKF, "2025-01-01T10:00:00Z", 100, "10"));
    Map<String, Map<LocalDate, BigDecimal>> flatNavs =
        Map.of(TKF, Map.of(LocalDate.parse("2025-01-01"), new BigDecimal("10")));

    Portfolio.GroupSummary summary =
        valuation(firstDay, flatNavs)
            .summaryOf(SAVINGS_FUND, LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-01"));

    assertThat(summary.startValue()).isEqualByComparingTo("0.00");
    assertThat(summary.contributions()).isEqualByComparingTo("1000.00");
    assertThat(summary.endValue()).isEqualByComparingTo("1000.00");
    assertThat(summary.gain()).isEqualByComparingTo("0.00");
  }
}
