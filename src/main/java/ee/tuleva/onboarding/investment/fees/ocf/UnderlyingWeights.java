package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfHolding.WEIGHT_SCALE;
import static ee.tuleva.onboarding.investment.fees.ocf.UnderlyingWeightBasis.MONTH_AVERAGE;
import static ee.tuleva.onboarding.investment.fees.ocf.UnderlyingWeightBasis.MONTH_END;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.fees.rate.InstrumentRate;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculation;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
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
      SortedMap<String, BigDecimal> byIsin,
      BigDecimal investedRatioMonthEnd,
      @Nullable BigDecimal investedRatioMonthAverage) {

    List<String> unratedIsins(Map<String, InstrumentRate> rates) {
      return OcfHolding.unratedIsins(byIsin.keySet(), rates);
    }

    List<OcfHolding> holdings(Map<String, InstrumentRate> rates) {
      return OcfHolding.of(byIsin, rates);
    }
  }

  Weights of(TulevaFund fund, YearMonth month, NavCalculation monthEndCalculation) {
    var monthEndInvestedRatio = investedRatio(monthEndCalculation);
    var publishedInMonth = publishedCalculationsIn(fund, month);
    if (publishedInMonth.isEmpty()) {
      return new Weights(MONTH_END, weightsOf(monthEndCalculation), monthEndInvestedRatio, null);
    }
    var averageInvestedRatio =
        mean(publishedInMonth.stream().map(UnderlyingWeights::investedRatio).toList());
    if (isRepresentative(monthEndInvestedRatio, averageInvestedRatio)) {
      return new Weights(
          MONTH_END, weightsOf(monthEndCalculation), monthEndInvestedRatio, averageInvestedRatio);
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
        averageWeights(publishedInMonth),
        monthEndInvestedRatio,
        averageInvestedRatio);
  }

  private List<NavCalculation> publishedCalculationsIn(TulevaFund fund, YearMonth month) {
    return fundNavQueryService
        .findPublishedNavDatesBetween(fund.getCode(), month.atDay(1), month.atEndOfMonth())
        .stream()
        .map(date -> fundNavQueryService.findPublishedCalculation(fund.getCode(), date))
        .flatMap(Optional::stream)
        .filter(calculation -> calculation.assetsUnderManagement().signum() > 0)
        .toList();
  }

  private boolean isRepresentative(BigDecimal monthEnd, BigDecimal average) {
    return monthEnd.subtract(average).abs().compareTo(investedRatioDeviationThreshold) <= 0;
  }

  private static SortedMap<String, BigDecimal> weightsOf(NavCalculation calculation) {
    return OcfHolding.weightsOf(calculation.securityLines(), calculation.assetsUnderManagement());
  }

  private static SortedMap<String, BigDecimal> averageWeights(List<NavCalculation> calculations) {
    var sums = new TreeMap<String, BigDecimal>();
    calculations.stream()
        .map(UnderlyingWeights::weightsOf)
        .forEach(
            weights ->
                weights.forEach((isin, weight) -> sums.merge(isin, weight, BigDecimal::add)));
    var days = BigDecimal.valueOf(calculations.size());
    var averages = new TreeMap<String, BigDecimal>();
    sums.forEach(
        (isin, sum) -> {
          if (sum.signum() != 0) {
            averages.put(isin, sum.divide(days, WEIGHT_SCALE, HALF_UP));
          }
        });
    return averages;
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
