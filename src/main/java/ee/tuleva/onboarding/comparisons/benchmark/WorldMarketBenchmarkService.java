package ee.tuleva.onboarding.comparisons.benchmark;

import static java.math.RoundingMode.HALF_UP;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class WorldMarketBenchmarkService {

  static final LocalDate EQUITY_CAP_LIFTED = LocalDate.of(2019, 9, 2);

  private static final String ACWI_KEY = "MSCI_ACWI";
  private static final String BOND_KEY = "EURO_AGGREGATE_BOND";
  private static final int MAX_WINDOW_YEARS = 10;
  private static final int ANCHOR_TOLERANCE_DAYS = 14;
  private static final double EQUITY_WEIGHT = 0.75;
  private static final double BOND_WEIGHT = 0.25;
  private static final double DAYS_PER_YEAR = 365.25;
  private static final int RETURN_SCALE = 6;

  private final FundValueRepository fundValueRepository;
  private final Clock clock;

  List<WorldMarketReturn> getReturns() {
    LocalDate today = LocalDate.now(clock);
    List<FundValue> acwiSeries =
        fundValueRepository.findValuesBetweenDates(
            ACWI_KEY, today.minusYears(MAX_WINDOW_YEARS).minusDays(ANCHOR_TOLERANCE_DAYS), today);
    if (acwiSeries.isEmpty()) {
      return List.of();
    }
    FundValue end = acwiSeries.getLast();
    List<FundValue> bondSeries = compositeEraBondSeries(end);
    return IntStream.rangeClosed(1, MAX_WINDOW_YEARS)
        .mapToObj(years -> windowReturn(acwiSeries, bondSeries, end, years))
        .flatMap(Optional::stream)
        .toList();
  }

  private List<FundValue> compositeEraBondSeries(FundValue end) {
    LocalDate earliestWindowStart = end.date().minusYears(MAX_WINDOW_YEARS);
    if (!earliestWindowStart.isBefore(EQUITY_CAP_LIFTED)) {
      return List.of();
    }
    return fundValueRepository.findValuesBetweenDates(
        BOND_KEY, earliestWindowStart.minusDays(ANCHOR_TOLERANCE_DAYS), EQUITY_CAP_LIFTED);
  }

  private Optional<WorldMarketReturn> windowReturn(
      List<FundValue> acwiSeries, List<FundValue> bondSeries, FundValue end, int years) {
    return anchorAtOrBefore(acwiSeries, end.date().minusYears(years))
        .flatMap(
            anchor -> {
              boolean composite = anchor.date().isBefore(EQUITY_CAP_LIFTED);
              Optional<Double> totalReturn =
                  composite
                      ? compositeTotalReturn(acwiSeries, bondSeries, anchor, end)
                      : Optional.of(ratio(end, anchor));
              return totalReturn.map(total -> annualized(total, anchor, end, years, composite));
            });
  }

  private Optional<Double> compositeTotalReturn(
      List<FundValue> acwiSeries, List<FundValue> bondSeries, FundValue anchor, FundValue end) {
    return anchorAtOrBefore(acwiSeries, EQUITY_CAP_LIFTED)
        .flatMap(
            acwiAtSwitch ->
                anchorAtOrBefore(bondSeries, anchor.date())
                    .flatMap(
                        bondAtAnchor ->
                            anchorAtOrBefore(bondSeries, EQUITY_CAP_LIFTED)
                                .map(
                                    bondAtSwitch -> {
                                      double cappedEraMix =
                                          EQUITY_WEIGHT * ratio(acwiAtSwitch, anchor)
                                              + BOND_WEIGHT * ratio(bondAtSwitch, bondAtAnchor);
                                      return cappedEraMix * ratio(end, acwiAtSwitch);
                                    })));
  }

  private WorldMarketReturn annualized(
      double totalReturn, FundValue anchor, FundValue end, int years, boolean composite) {
    long days = DAYS.between(anchor.date(), end.date());
    double annualizedReturn = Math.pow(totalReturn, DAYS_PER_YEAR / days) - 1;
    return new WorldMarketReturn(
        years,
        BigDecimal.valueOf(annualizedReturn).setScale(RETURN_SCALE, HALF_UP),
        anchor.date(),
        end.date(),
        composite);
  }

  private static double ratio(FundValue numerator, FundValue denominator) {
    return numerator.value().doubleValue() / denominator.value().doubleValue();
  }

  private static Optional<FundValue> anchorAtOrBefore(
      List<FundValue> series, LocalDate targetDate) {
    return series.stream()
        .filter(value -> !value.date().isAfter(targetDate))
        .reduce((first, second) -> second)
        .filter(anchor -> !anchor.date().isBefore(targetDate.minusDays(ANCHOR_TOLERANCE_DAYS)));
  }
}
