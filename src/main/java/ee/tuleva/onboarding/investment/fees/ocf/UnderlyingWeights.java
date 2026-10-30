package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfHolding.WEIGHT_SCALE;
import static ee.tuleva.onboarding.investment.fees.ocf.UnderlyingWeightBasis.MONTH_AVERAGE;
import static ee.tuleva.onboarding.investment.fees.ocf.UnderlyingWeightBasis.MONTH_END;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.investment.fees.rate.ApplicableRate;
import ee.tuleva.onboarding.investment.fees.rate.DailyRates;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculation;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class UnderlyingWeights {

  private final FundNavQueryService fundNavQueryService;
  private final BigDecimal investedRatioDeviationThreshold;

  UnderlyingWeights(
      FundNavQueryService fundNavQueryService,
      @Value("${investment.ocf.invested-ratio-deviation-threshold:0.015}")
          BigDecimal investedRatioDeviationThreshold) {
    this.fundNavQueryService = fundNavQueryService;
    this.investedRatioDeviationThreshold = investedRatioDeviationThreshold;
  }

  record Weights(
      UnderlyingWeightBasis basis,
      List<DayWeights> days,
      BigDecimal investedRatioMonthEnd,
      @Nullable BigDecimal investedRatioMonthAverage) {

    List<String> unratedIsins(DailyRates rates) {
      return days.stream()
          .flatMap(day -> day.heldIsins().filter(isin -> rates.on(isin, day.date()).isEmpty()))
          .distinct()
          .sorted()
          .toList();
    }

    List<OcfHolding> holdings(DailyRates rates) {
      var weightByRate = new LinkedHashMap<ApplicableRate, BigDecimal>();
      days.forEach(day -> day.addTo(weightByRate, rates));
      var dayCount = BigDecimal.valueOf(days.size());
      return weightByRate.entrySet().stream()
          .map(
              weight ->
                  new OcfHolding(
                      weight.getValue().divide(dayCount, WEIGHT_SCALE, HALF_UP), weight.getKey()))
          .sorted(
              comparing((OcfHolding holding) -> holding.rate().isin())
                  .thenComparing(holding -> holding.rate().agreementId())
                  .thenComparing(holding -> holding.rate().basis()))
          .toList();
    }
  }

  record DayWeights(LocalDate date, SortedMap<String, BigDecimal> byIsin) {

    Stream<String> heldIsins() {
      return byIsin.entrySet().stream()
          .filter(weight -> weight.getValue().signum() != 0)
          .map(Map.Entry::getKey);
    }

    void addTo(Map<ApplicableRate, BigDecimal> weightByRate, DailyRates rates) {
      heldIsins()
          .forEach(
              isin ->
                  weightByRate.merge(
                      rates
                          .on(isin, date)
                          .orElseThrow(
                              () ->
                                  new IllegalStateException(
                                      "Unrated holding passed the guard: " + isin)),
                      byIsin.get(isin),
                      BigDecimal::add));
    }
  }

  Weights of(TulevaFund fund, YearMonth month, NavCalculation monthEndCalculation) {
    var monthEnd = List.of(new DayWeights(month.atEndOfMonth(), weightsOf(monthEndCalculation)));
    var monthEndInvestedRatio = investedRatio(monthEndCalculation);
    var publishedInMonth = publishedCalculationsIn(fund, month);
    if (publishedInMonth.isEmpty()) {
      return new Weights(MONTH_END, monthEnd, monthEndInvestedRatio, null);
    }
    var averageInvestedRatio =
        mean(publishedInMonth.values().stream().map(UnderlyingWeights::investedRatio).toList());
    if (isRepresentative(monthEndInvestedRatio, averageInvestedRatio)) {
      return new Weights(MONTH_END, monthEnd, monthEndInvestedRatio, averageInvestedRatio);
    }
    log.warn(
        "Month-end invested ratio is not representative, underlying funds are weighed by their"
            + " month-average share: fund={}, month={}, monthEndInvestedRatio={},"
            + " averageInvestedRatio={}",
        fund.getCode(),
        month,
        monthEndInvestedRatio,
        averageInvestedRatio);
    return new Weights(
        MONTH_AVERAGE,
        publishedInMonth.entrySet().stream()
            .map(published -> new DayWeights(published.getKey(), weightsOf(published.getValue())))
            .toList(),
        monthEndInvestedRatio,
        averageInvestedRatio);
  }

  private SortedMap<LocalDate, NavCalculation> publishedCalculationsIn(
      TulevaFund fund, YearMonth month) {
    var calculations = new TreeMap<LocalDate, NavCalculation>();
    fundNavQueryService
        .findPublishedNavDatesBetween(fund.getCode(), month.atDay(1), month.atEndOfMonth())
        .forEach(
            date ->
                fundNavQueryService
                    .findPublishedCalculation(fund.getCode(), date)
                    .filter(calculation -> calculation.assetsUnderManagement().signum() > 0)
                    .ifPresent(calculation -> calculations.put(date, calculation)));
    return calculations;
  }

  private boolean isRepresentative(BigDecimal monthEnd, BigDecimal average) {
    return monthEnd.subtract(average).abs().compareTo(investedRatioDeviationThreshold) <= 0;
  }

  private static SortedMap<String, BigDecimal> weightsOf(NavCalculation calculation) {
    return OcfHolding.weightsOf(calculation.securityLines(), calculation.assetsUnderManagement());
  }

  private static BigDecimal investedRatio(NavCalculation calculation) {
    return calculation.securityLines().stream()
        .map(NavAccountLine::value)
        .reduce(ZERO, BigDecimal::add)
        .divide(calculation.assetsUnderManagement(), WEIGHT_SCALE, HALF_UP);
  }

  private static BigDecimal mean(List<BigDecimal> values) {
    return values.stream()
        .reduce(ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(values.size()), WEIGHT_SCALE, HALF_UP);
  }
}
