package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioPresentation.ANNEX_VI_P90_EXAMPLE_INVESTMENT_EUR;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorProperties;
import ee.tuleva.onboarding.investment.series.SeriesSegment;
import ee.tuleva.onboarding.investment.series.SeriesSpliceRefusedException;
import ee.tuleva.onboarding.investment.series.SplicedPriceSeries;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class KidFiguresService {

  private static final int FIRST_YEAR = 1;
  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final KidFunds kidFunds;
  private final RiskIndicatorProperties riskIndicatorSeries;
  private final SplicedPriceSeries priceSeries;
  private final Clock clock;
  private final PerformanceScenarioCalculator performanceScenarioCalculator;
  private final StressScenarioCalculator stressScenarioCalculator;
  private final ScenarioPresentation scenarioPresentation;
  private final CostsOverTimeCalculator costsOverTimeCalculator;

  KidFigures figures(TulevaFund fund, BigDecimal ongoingCharges) {
    var holdingPeriodYears = kidFunds.settingsFor(fund).recommendedHoldingPeriodYears();
    var series = riskIndicatorSeries.sourcesFor(fund);
    var charges = ongoingCharges.doubleValue();

    var period =
        ScenarioPeriod.endingWithLastCompleteMonth(
            lastPriceDate(series), LocalDate.now(clock.withZone(ESTONIAN_ZONE)));
    var prices = positive(spliced(series, period));
    var monthEndPrices =
        OwnNavMonthEnds.of(fund, series).grossedUp(period.monthEndPrices(prices), charges);
    var riskHistory = period.riskHistory(prices);

    var firstYearScenarios =
        performanceScenarioCalculator.scenarios(monthEndPrices, FIRST_YEAR, charges);
    var holdingPeriodScenarios =
        performanceScenarioCalculator.scenarios(monthEndPrices, holdingPeriodYears, charges);
    var scenarios =
        Stream.concat(
                present(FIRST_YEAR, firstYearScenarios, riskHistory, period.end(), charges)
                    .stream(),
                present(
                    holdingPeriodYears, holdingPeriodScenarios, riskHistory, period.end(), charges)
                    .stream())
            .toList();

    return new KidFigures(
        fund,
        series,
        period.start(),
        period.end(),
        ongoingCharges,
        holdingPeriodYears,
        ANNEX_VI_P90_EXAMPLE_INVESTMENT_EUR,
        scenarios,
        List.of(
            costsOverTimeCalculator.firstYearAtZeroNetReturn(charges),
            costsOverTimeCalculator.holdingPeriod(
                holdingPeriodYears, holdingPeriodScenarios.moderate(), charges)));
  }

  private List<KidScenario> present(
      int years,
      PerformanceScenarios performance,
      List<FundValue> riskHistory,
      LocalDate periodEnd,
      double charges) {
    return scenarioPresentation.present(
        years,
        performance,
        stressScenarioCalculator.stress(riskHistory, periodEnd, years),
        charges);
  }

  private static List<FundValue> positive(List<FundValue> prices) {
    prices.stream()
        .filter(price -> price.value().signum() <= 0)
        .findFirst()
        .ifPresent(
            price -> {
              throw new KidCalculationRefusedException(
                  "Price not positive: key=%s, date=%s".formatted(price.key(), price.date()));
            });
    return prices;
  }

  private List<FundValue> spliced(List<SeriesSegment> series, ScenarioPeriod period) {
    try {
      return priceSeries.closes(series, period.loadFrom(), period.end());
    } catch (SeriesSpliceRefusedException refusal) {
      throw new KidCalculationRefusedException("KID series not spliced: " + refusal.getMessage());
    }
  }

  private LocalDate lastPriceDate(List<SeriesSegment> series) {
    return priceSeries
        .lastCloseDate(series)
        .orElseThrow(
            () ->
                new KidCalculationRefusedException(
                    "No prices for the KID series: key=" + SplicedPriceSeries.key(series)));
  }
}
