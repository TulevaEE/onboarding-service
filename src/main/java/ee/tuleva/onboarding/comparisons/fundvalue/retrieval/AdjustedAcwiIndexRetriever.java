package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.MsciIndexConfiguration.MSCI_ACWI_KEY;
import static java.math.MathContext.DECIMAL64;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.comparisons.fundvalue.EquityCap;
import ee.tuleva.onboarding.comparisons.fundvalue.EquityCappedSeries;
import ee.tuleva.onboarding.comparisons.fundvalue.EquityCappedSeriesRefusedException;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class AdjustedAcwiIndexRetriever implements ComparisonIndexRetriever {

  @ToString.Include public static final String KEY = "MSCI_ACWI_ADJUSTED";
  static final String PROVIDER = "CALCULATED";

  private static final String EURO_BOND_LEG = "EURO_BOND_LEG";
  private static final EquityCap
      II_PILLAR_HALF_IN_SHARES_COUNTING_EQUITY_FUNDS_UNTIL_THE_75_PERCENT_LIMIT_OF_14_NOVEMBER_2008 =
          new EquityCap(EURO_BOND_LEG, new BigDecimal("0.50"), LocalDate.of(2008, 11, 13));
  private static final EquityCap
      II_PILLAR_THREE_QUARTERS_IN_SHARES_AND_EQUITY_FUNDS_UNTIL_THE_CAP_WAS_LIFTED_2_SEPTEMBER_2019 =
          new EquityCap(EURO_BOND_LEG, new BigDecimal("0.75"), LocalDate.of(2019, 9, 2));
  private static final List<EquityCap> II_PILLAR_EQUITY_CAPS =
      List.of(
          II_PILLAR_HALF_IN_SHARES_COUNTING_EQUITY_FUNDS_UNTIL_THE_75_PERCENT_LIMIT_OF_14_NOVEMBER_2008,
          II_PILLAR_THREE_QUARTERS_IN_SHARES_AND_EQUITY_FUNDS_UNTIL_THE_CAP_WAS_LIFTED_2_SEPTEMBER_2019);
  private static final LocalDate SERIES_START = EuroGovernmentBondRetriever.SERIES_START;
  private static final LocalDate LAST_CAPPED_DAY =
      II_PILLAR_THREE_QUARTERS_IN_SHARES_AND_EQUITY_FUNDS_UNTIL_THE_CAP_WAS_LIFTED_2_SEPTEMBER_2019
          .liftedOn();
  private static final int BOND_CLOSE_AT_MOST_DAYS_OLD = 7;
  private static final int INDEX_VALUES_SCALE = 5;

  private final FundValueQueries fundValueQueries;
  private final Clock clock;

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public Duration stalenessThreshold() {
    return Duration.ofDays(5);
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    var acwi = fundValueQueries.findValuesBetweenDates(MSCI_ACWI_KEY, SERIES_START, endDate);
    if (acwi.stream().noneMatch(close -> !close.date().isBefore(startDate))) {
      return List.of();
    }
    var bondLeg = bondLeg();
    refuseUnlessTheBondLegCoversEveryCappedDay(acwi, bondLeg);
    var now = clock.instant();
    return adjusted(acwi, bondLeg).stream()
        .filter(level -> !level.date().isBefore(startDate) && !level.date().isAfter(endDate))
        .map(
            level ->
                new FundValue(
                    KEY,
                    level.date(),
                    level.value().setScale(INDEX_VALUES_SCALE, HALF_UP),
                    PROVIDER,
                    now))
        .toList();
  }

  private List<FundValue> adjusted(List<FundValue> acwi, List<FundValue> bondLeg) {
    try {
      return EquityCappedSeries.of(acwi, bondLeg, II_PILLAR_EQUITY_CAPS, SERIES_START);
    } catch (EquityCappedSeriesRefusedException refusal) {
      throw new IllegalStateException(
          "Adjusted ACWI cannot be calculated from the stored series: key="
              + KEY
              + ", reason="
              + refusal.getMessage(),
          refusal);
    }
  }

  private List<FundValue> bondLeg() {
    var governmentBonds =
        fundValueQueries.findValuesBetweenDates(
            EuroGovernmentBondRetriever.KEY,
            EuroGovernmentBondRetriever.SERIES_START,
            EuroGovernmentBondRetriever.SERIES_END);
    var aggregateBonds =
        fundValueQueries.findValuesBetweenDates(
            EuroAggregateBondRetriever.KEY,
            EuroAggregateBondRetriever.SERIES_START,
            EuroAggregateBondRetriever.SERIES_END);
    if (governmentBonds.isEmpty() || aggregateBonds.isEmpty()) {
      throw new ComparisonIndexUnavailableException(
          "Bond leg of the adjusted ACWI is not stored yet: key="
              + KEY
              + ", governmentBondCloses="
              + governmentBonds.size()
              + ", aggregateBondCloses="
              + aggregateBonds.size());
    }
    return joined(governmentBonds, aggregateBonds);
  }

  private static List<FundValue> joined(
      List<FundValue> governmentBonds, List<FundValue> aggregateBonds) {
    var lastGovernment = governmentBonds.getLast();
    var firstAggregate = aggregateBonds.getFirst();
    if (lastGovernment
        .date()
        .isBefore(firstAggregate.date().minusDays(BOND_CLOSE_AT_MOST_DAYS_OLD))) {
      throw new IllegalStateException(
          "Government bonds stop too long before aggregate bonds start: key="
              + KEY
              + ", lastGovernmentBondClose="
              + lastGovernment.date()
              + ", firstAggregateBondClose="
              + firstAggregate.date());
    }
    var toAggregateLevel = firstAggregate.value().divide(lastGovernment.value(), DECIMAL64);
    return Stream.concat(
            governmentBonds.stream()
                .map(
                    close ->
                        new FundValue(
                            EURO_BOND_LEG,
                            close.date(),
                            close.value().multiply(toAggregateLevel, DECIMAL64),
                            close.provider(),
                            close.updatedAt())),
            aggregateBonds.stream())
        .toList();
  }

  private static void refuseUnlessTheBondLegCoversEveryCappedDay(
      List<FundValue> acwi, List<FundValue> bondLeg) {
    var bondCloses = new TreeMap<LocalDate, FundValue>();
    bondLeg.forEach(close -> bondCloses.put(close.date(), close));
    acwi.stream()
        .map(FundValue::date)
        .filter(day -> !day.isAfter(LAST_CAPPED_DAY))
        .filter(
            day -> {
              var bondCloseDay = bondCloses.floorKey(day);
              return bondCloseDay == null
                  || bondCloseDay.isBefore(day.minusDays(BOND_CLOSE_AT_MOST_DAYS_OLD));
            })
        .findFirst()
        .ifPresent(
            day -> {
              throw new IllegalStateException(
                  "Bond leg has no close in the week before a day held at the equity cap: key="
                      + KEY
                      + ", day="
                      + day);
            });
  }
}
