package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioPresentation.ANNEX_VI_P90_EXAMPLE_INVESTMENT_EUR;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
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

  private final KidProperties properties;
  private final FundValueQueries fundValueQueries;
  private final Clock clock;
  private final PerformanceScenarioCalculator performanceScenarioCalculator;
  private final StressScenarioCalculator stressScenarioCalculator;
  private final ScenarioPresentation scenarioPresentation;
  private final CostsOverTimeCalculator costsOverTimeCalculator;

  KidFigures figures(TulevaFund fund, BigDecimal ongoingCharges) {
    var settings = properties.settingsFor(fund);
    var seriesKey = settings.seriesKey();
    var holdingPeriodYears = settings.recommendedHoldingPeriodYears();
    var charges = ongoingCharges.doubleValue();

    var period =
        ScenarioPeriod.endingWithLastCompleteMonth(
            lastPriceDate(seriesKey), LocalDate.now(clock.withZone(ESTONIAN_ZONE)));
    var prices =
        positive(
            fundValueQueries.findValuesBetweenDates(seriesKey, period.loadFrom(), period.end()));
    var monthEndPrices = period.monthEndPrices(prices);
    var dailyCloses = period.dailyCloses(prices);

    var firstYearScenarios =
        performanceScenarioCalculator.scenarios(monthEndPrices, FIRST_YEAR, charges);
    var holdingPeriodScenarios =
        performanceScenarioCalculator.scenarios(monthEndPrices, holdingPeriodYears, charges);
    var scenarios =
        Stream.concat(
                present(FIRST_YEAR, firstYearScenarios, dailyCloses, period.end(), charges)
                    .stream(),
                present(
                    holdingPeriodYears, holdingPeriodScenarios, dailyCloses, period.end(), charges)
                    .stream())
            .toList();

    return new KidFigures(
        fund,
        seriesKey,
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
      List<FundValue> dailyCloses,
      LocalDate periodEnd,
      double charges) {
    return scenarioPresentation.present(
        years,
        performance,
        stressScenarioCalculator.stress(dailyCloses, periodEnd, years),
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

  private LocalDate lastPriceDate(String seriesKey) {
    return fundValueQueries
        .findLastValueForFund(seriesKey)
        .map(FundValue::date)
        .orElseThrow(
            () ->
                new KidCalculationRefusedException(
                    "No prices for the KID series: key=" + seriesKey));
  }
}
