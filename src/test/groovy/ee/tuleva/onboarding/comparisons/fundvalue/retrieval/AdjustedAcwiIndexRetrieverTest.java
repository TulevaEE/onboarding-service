package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AdjustedAcwiIndexRetrieverTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

  private final FundValueQueries fundValueQueries = mock(FundValueQueries.class);
  private final AdjustedAcwiIndexRetriever retriever =
      new AdjustedAcwiIndexRetriever(fundValueQueries, Clock.fixed(NOW, ZoneOffset.UTC));

  private static final List<FundValue> ACWI =
      List.of(
          acwi("2003-01-07", 100),
          acwi("2007-12-31", 150),
          acwi("2008-11-13", 90),
          acwi("2008-12-31", 80),
          acwi("2016-05-31", 160),
          acwi("2016-12-30", 170),
          acwi("2019-09-02", 200),
          acwi("2019-12-31", 220));
  private static final List<FundValue> GOVERNMENT_BONDS =
      List.of(
          government("2003-01-07", 50),
          government("2007-12-31", 60),
          government("2008-11-13", 63),
          government("2008-12-31", 64),
          government("2016-05-31", 90));
  private static final List<FundValue> AGGREGATE_BONDS =
      List.of(
          aggregate("2016-06-01", 120),
          aggregate("2016-12-30", 118),
          aggregate("2019-09-02", 130),
          aggregate("2019-09-09", 131));

  @Test
  void holdsHalfInBondsWhileTheLawCountedEquityFundsIntoTheFiftyPercentShareLimit() {
    givenTheSeries(ACWI, GOVERNMENT_BONDS, AGGREGATE_BONDS);

    var index = retriever.retrieveValuesForRange(date("2003-01-07"), date("2019-12-31"));

    assertThat(growth(index, "2003-01-07", "2007-12-31"))
        .isCloseTo(0.5 * 150 / 100 + 0.5 * 60 / 50, within(1e-6));
    assertThat(growth(index, "2007-12-31", "2008-11-13"))
        .isCloseTo(0.5 * 90 / 150 + 0.5 * 63 / 60, within(1e-6));
  }

  @Test
  void movesToThreeQuartersInStocksTheDayEquityFundsGotTheirOwnSeventyFivePercentLimit() {
    givenTheSeries(ACWI, GOVERNMENT_BONDS, AGGREGATE_BONDS);

    var index = retriever.retrieveValuesForRange(date("2003-01-07"), date("2019-12-31"));

    assertThat(growth(index, "2008-11-13", "2008-12-31"))
        .isCloseTo(0.75 * 80 / 90 + 0.25 * 64 / 63, within(1e-6));
  }

  @Test
  void continuesTheBondLegFromGovernmentBondsIntoAggregateBondsWithoutAJump() {
    givenTheSeries(ACWI, GOVERNMENT_BONDS, AGGREGATE_BONDS);

    var index = retriever.retrieveValuesForRange(date("2003-01-07"), date("2019-12-31"));

    assertThat(growth(index, "2008-12-31", "2016-12-30"))
        .isCloseTo(0.75 * 170 / 80 + 0.25 * (90.0 / 64) * (118.0 / 120), within(1e-6));
  }

  @Test
  void isOnlyStocksFromTheDayThePensionFundEquityCapWasLifted() {
    givenTheSeries(ACWI, GOVERNMENT_BONDS, AGGREGATE_BONDS);

    var index = retriever.retrieveValuesForRange(date("2003-01-07"), date("2019-12-31"));

    assertThat(growth(index, "2016-12-30", "2019-09-02"))
        .isCloseTo(0.75 * 200 / 170 + 0.25 * 130 / 118, within(1e-6));
    assertThat(growth(index, "2019-09-02", "2019-12-31")).isCloseTo(220.0 / 200, within(1e-6));
  }

  @Test
  void storesEveryAcwiCloseInTheRangeUnderItsOwnKeyAsACalculatedValue() {
    givenTheSeries(ACWI, GOVERNMENT_BONDS, AGGREGATE_BONDS);

    var index = retriever.retrieveValuesForRange(date("2016-05-31"), date("2019-09-02"));

    assertThat(index)
        .extracting(FundValue::key, FundValue::date, FundValue::provider, FundValue::updatedAt)
        .containsExactly(
            tuple("MSCI_ACWI_ADJUSTED", date("2016-05-31"), "CALCULATED", NOW),
            tuple("MSCI_ACWI_ADJUSTED", date("2016-12-30"), "CALCULATED", NOW),
            tuple("MSCI_ACWI_ADJUSTED", date("2019-09-02"), "CALCULATED", NOW));
  }

  @Test
  void roundsEveryLevelToTheFiveDecimalsTheIndexTableHolds() {
    givenTheSeries(ACWI, GOVERNMENT_BONDS, AGGREGATE_BONDS);

    var index = retriever.retrieveValuesForRange(date("2003-01-07"), date("2019-12-31"));

    assertThat(index).extracting(value -> value.value().scale()).containsOnly(5);
  }

  @Test
  void refusesToWriteAnythingWhileTheGovernmentBondYearsAreNotStoredYet() {
    givenTheSeries(ACWI, List.of(), AGGREGATE_BONDS);

    assertThatThrownBy(
            () -> retriever.retrieveValuesForRange(date("2003-01-07"), date("2019-12-31")))
        .isInstanceOf(ComparisonIndexUnavailableException.class);
  }

  @Test
  void refusesToWriteAnythingWhileTheAggregateBondYearsAreNotStoredYet() {
    givenTheSeries(ACWI, GOVERNMENT_BONDS, List.of());

    assertThatThrownBy(
            () -> retriever.retrieveValuesForRange(date("2003-01-07"), date("2019-12-31")))
        .isInstanceOf(ComparisonIndexUnavailableException.class);
  }

  @Test
  void failsLoudlyWhenADayHeldAtTheCapHasNoBondCloseInTheWeekBeforeIt() {
    var acwiWithAMidYearDay =
        Stream.concat(ACWI.stream(), Stream.of(acwi("2012-06-15", 120), acwi("2012-12-31", 125)))
            .sorted(Comparator.comparing(FundValue::date))
            .toList();
    var governmentBondsWithoutThatDay =
        Stream.concat(GOVERNMENT_BONDS.stream(), Stream.of(government("2012-12-31", 80)))
            .sorted(Comparator.comparing(FundValue::date))
            .toList();
    givenTheSeries(acwiWithAMidYearDay, governmentBondsWithoutThatDay, AGGREGATE_BONDS);

    assertThatThrownBy(
            () -> retriever.retrieveValuesForRange(date("2003-01-07"), date("2019-12-31")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsLoudlyWhenTheGovernmentBondsStopMoreThanAWeekBeforeTheAggregateBondsStart() {
    var governmentBondsEndingEarly =
        List.of(
            government("2003-01-07", 50),
            government("2007-12-31", 60),
            government("2008-11-13", 63),
            government("2008-12-31", 64),
            government("2016-05-20", 90));
    var acwiWithoutTheDayBeforeTheJoin =
        ACWI.stream().filter(close -> !close.date().equals(date("2016-05-31"))).toList();
    givenTheSeries(acwiWithoutTheDayBeforeTheJoin, governmentBondsEndingEarly, AGGREGATE_BONDS);

    assertThatThrownBy(
            () -> retriever.retrieveValuesForRange(date("2003-01-07"), date("2019-12-31")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void returnsNothingWithoutReadingTheBondsWhenAcwiHasNoNewCloseInTheRange() {
    given(fundValueQueries.findValuesBetweenDates(eq("MSCI_ACWI"), any(), any())).willReturn(ACWI);

    assertThat(retriever.retrieveValuesForRange(date("2020-01-01"), date("2026-11-12"))).isEmpty();
    then(fundValueQueries)
        .should(never())
        .findValuesBetweenDates(eq("EURO_GOVERNMENT_BOND"), any(), any());
  }

  @Test
  void returnsNothingBeforeAcwiHasAnyClose() {
    givenTheSeries(List.of(), GOVERNMENT_BONDS, AGGREGATE_BONDS);

    assertThat(retriever.retrieveValuesForRange(date("2003-01-07"), date("2019-12-31"))).isEmpty();
  }

  @Test
  void isKeyedAsTheAdjustedAcwiIndexAndStalesLikeAcwiItself() {
    assertThat(retriever.getKey()).isEqualTo("MSCI_ACWI_ADJUSTED");
    assertThat(retriever.stalenessThreshold()).isEqualTo(Duration.ofDays(5));
  }

  private void givenTheSeries(
      List<FundValue> acwi, List<FundValue> governmentBonds, List<FundValue> aggregateBonds) {
    given(fundValueQueries.findValuesBetweenDates(eq("MSCI_ACWI"), any(), any())).willReturn(acwi);
    given(fundValueQueries.findValuesBetweenDates(eq("EURO_GOVERNMENT_BOND"), any(), any()))
        .willReturn(governmentBonds);
    given(fundValueQueries.findValuesBetweenDates(eq("EURO_AGGREGATE_BOND"), any(), any()))
        .willReturn(aggregateBonds);
  }

  private static double growth(List<FundValue> index, String from, String to) {
    return valueOn(index, to) / valueOn(index, from);
  }

  private static double valueOn(List<FundValue> index, String day) {
    return index.stream()
        .filter(value -> value.date().equals(date(day)))
        .findFirst()
        .orElseThrow()
        .value()
        .doubleValue();
  }

  private static LocalDate date(String day) {
    return LocalDate.parse(day);
  }

  private static FundValue acwi(String day, double value) {
    return aFundValue("MSCI_ACWI", date(day), value);
  }

  private static FundValue government(String day, double value) {
    return aFundValue("EURO_GOVERNMENT_BOND", date(day), value);
  }

  private static FundValue aggregate(String day, double value) {
    return aFundValue("EURO_AGGREGATE_BOND", date(day), value);
  }
}
