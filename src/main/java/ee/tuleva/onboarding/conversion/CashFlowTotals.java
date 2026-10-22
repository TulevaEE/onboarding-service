package ee.tuleva.onboarding.conversion;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.conversion.ConversionResponse.Amount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Predicate;

class CashFlowTotals {

  private final List<ConversionCashFlow> cashFlows;
  private final ZonedDateTime now;

  CashFlowTotals(List<ConversionCashFlow> cashFlows, Clock clock) {
    this.cashFlows = cashFlows;
    this.now = ZonedDateTime.now(clock);
  }

  Amount contribution(int pillar) {
    return amount(pillar, ConversionCashFlow::cashContribution, ConversionCashFlow::contribution);
  }

  Amount subtraction(int pillar) {
    return amount(pillar, ConversionCashFlow::subtraction, ConversionCashFlow::subtraction);
  }

  boolean paymentComplete(int pillar) {
    var sameTimeLastYear = now.minusYears(1).toInstant();
    return cashFlows.stream()
            .filter(cashFlow -> cashFlow.time().isAfter(sameTimeLastYear))
            .filter(ConversionCashFlow::cashContribution)
            .filter(cashFlow -> cashFlow.pillar() == pillar)
            .map(ConversionCashFlow::amount)
            .reduce(ZERO, BigDecimal::add)
            .compareTo(ZERO)
        > 0;
  }

  private Amount amount(
      int pillar,
      Predicate<ConversionCashFlow> countsPerYear,
      Predicate<ConversionCashFlow> countsInTotal) {
    return Amount.builder()
        .yearToDate(sum(pillar, countsPerYear.and(inYear(now.getYear()))))
        .lastYear(sum(pillar, countsPerYear.and(inYear(now.getYear() - 1))))
        .total(sum(pillar, countsInTotal))
        .build();
  }

  private Predicate<ConversionCashFlow> inYear(int year) {
    return cashFlow -> cashFlow.time().atZone(now.getZone()).getYear() == year;
  }

  private BigDecimal sum(int pillar, Predicate<ConversionCashFlow> filterBy) {
    return cashFlows.stream()
        .filter(filterBy)
        .filter(cashFlow -> cashFlow.pillar() == pillar)
        .map(ConversionCashFlow::amount)
        .reduce(ZERO, BigDecimal::add)
        .setScale(2, HALF_UP);
  }
}
