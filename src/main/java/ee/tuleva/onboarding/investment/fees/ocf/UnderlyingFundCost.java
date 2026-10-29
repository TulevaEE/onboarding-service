package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService.SCALE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.fees.rate.InstrumentRate;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

record UnderlyingFundCost(
    BigDecimal rate,
    BigDecimal grossRate,
    @Nullable LocalDate navDate,
    @Nullable UUID navCalculationId,
    @Nullable BigDecimal assetsUnderManagement,
    List<OcfGap> gaps,
    List<OcfHolding> holdings,
    @Nullable UnderlyingWeightBasis weightBasis,
    @Nullable BigDecimal investedRatioMonthEnd,
    @Nullable BigDecimal investedRatioMonthAverage) {

  static UnderlyingFundCost notWeighed(
      @Nullable LocalDate navDate,
      @Nullable UUID navCalculationId,
      @Nullable BigDecimal assetsUnderManagement,
      List<OcfGap> gaps) {
    return new UnderlyingFundCost(
        ZERO,
        ZERO,
        navDate,
        navCalculationId,
        assetsUnderManagement,
        gaps,
        List.of(),
        null,
        null,
        null);
  }

  static UnderlyingFundCost weighed(
      LocalDate navDate,
      NavCalculation calculation,
      List<OcfGap> gaps,
      UnderlyingWeights.Weights weights,
      Map<String, InstrumentRate> rates) {
    var holdings = weights.holdings(rates);
    return new UnderlyingFundCost(
        weigh(holdings, InstrumentRate::netOcf),
        weigh(holdings, InstrumentRate::publishedOcf),
        navDate,
        calculation.id(),
        calculation.assetsUnderManagement(),
        gaps,
        holdings,
        weights.basis(),
        weights.investedRatioMonthEnd(),
        weights.investedRatioMonthAverage());
  }

  private static BigDecimal weigh(
      List<OcfHolding> holdings, Function<InstrumentRate, BigDecimal> rate) {
    return holdings.stream()
        .map(holding -> holding.weight().multiply(rate.apply(holding.rate())))
        .reduce(ZERO, BigDecimal::add)
        .setScale(SCALE, HALF_UP);
  }
}
