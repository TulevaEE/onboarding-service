package ee.tuleva.onboarding.investment.series;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SplicedPriceSeriesTest {

  private static final String INDEX = "MSCI_ACWI";
  private static final String OWN_NAV = "EE0000003283";
  private static final LocalDate OWN_NAV_FROM = LocalDate.of(2026, 3, 1);
  private static final LocalDate FROM = LocalDate.of(2026, 2, 25);
  private static final LocalDate TO = LocalDate.of(2026, 3, 3);
  private static final List<SeriesSegment> INDEX_THEN_OWN_NAV =
      List.of(new SeriesSegment(INDEX, null), new SeriesSegment(OWN_NAV, OWN_NAV_FROM));

  @Mock private FundValueQueries fundValueQueries;
  @InjectMocks private SplicedPriceSeries series;

  @Test
  void oneSegmentIsItsOwnClosesUnderItsOwnKey() {
    var closes =
        List.of(
            aFundValue(INDEX, LocalDate.of(2026, 2, 25), 200.0),
            aFundValue(INDEX, LocalDate.of(2026, 2, 26), 202.0));
    given(fundValueQueries.findValuesBetweenDates(INDEX, FROM, TO)).willReturn(closes);

    var spliced = series.closes(List.of(new SeriesSegment(INDEX, null)), FROM, TO);

    assertThat(spliced).isEqualTo(closes);
  }

  @Test
  void aLaterSegmentContinuesFromThePreviousSegmentsLastCloseWithItsOwnReturns() {
    given(fundValueQueries.findValuesBetweenDates(INDEX, FROM, LocalDate.of(2026, 2, 28)))
        .willReturn(
            List.of(
                aFundValue(INDEX, LocalDate.of(2026, 2, 25), 200.0),
                aFundValue(INDEX, LocalDate.of(2026, 2, 26), 202.0),
                aFundValue(INDEX, LocalDate.of(2026, 2, 27), 204.0)));
    given(fundValueQueries.findValuesBetweenDates(OWN_NAV, LocalDate.of(2026, 2, 27), TO))
        .willReturn(
            List.of(
                aFundValue(OWN_NAV, LocalDate.of(2026, 2, 27), 1.00),
                aFundValue(OWN_NAV, LocalDate.of(2026, 3, 2), 1.01),
                aFundValue(OWN_NAV, LocalDate.of(2026, 3, 3), 0.99)));

    var spliced = series.closes(INDEX_THEN_OWN_NAV, FROM, TO);

    var key = "MSCI_ACWI+EE0000003283";
    assertThat(spliced)
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("updatedAt")
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .containsExactly(
            aFundValue(key, LocalDate.of(2026, 2, 25), 200.0),
            aFundValue(key, LocalDate.of(2026, 2, 26), 202.0),
            aFundValue(key, LocalDate.of(2026, 2, 27), 204.0),
            aFundValue(key, LocalDate.of(2026, 3, 2), 206.04),
            aFundValue(key, LocalDate.of(2026, 3, 3), 201.96));
  }

  @Test
  void aLaterSegmentStartsOnItsOwnStartDateEvenWhenTheEarlierSegmentEndedDaysBefore() {
    given(fundValueQueries.findValuesBetweenDates(INDEX, FROM, LocalDate.of(2026, 2, 28)))
        .willReturn(
            List.of(
                aFundValue(INDEX, LocalDate.of(2026, 2, 25), 200.0),
                aFundValue(INDEX, LocalDate.of(2026, 2, 26), 202.0)));
    given(fundValueQueries.findValuesBetweenDates(OWN_NAV, LocalDate.of(2026, 2, 26), TO))
        .willReturn(
            List.of(
                aFundValue(OWN_NAV, LocalDate.of(2026, 2, 26), 1.00),
                aFundValue(OWN_NAV, LocalDate.of(2026, 2, 27), 1.02),
                aFundValue(OWN_NAV, LocalDate.of(2026, 3, 2), 1.01)));

    var spliced = series.closes(INDEX_THEN_OWN_NAV, FROM, TO);

    var key = "MSCI_ACWI+EE0000003283";
    assertThat(spliced)
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("updatedAt")
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .containsExactly(
            aFundValue(key, LocalDate.of(2026, 2, 25), 200.0),
            aFundValue(key, LocalDate.of(2026, 2, 26), 202.0),
            aFundValue(key, LocalDate.of(2026, 3, 2), 204.02));
  }

  @Test
  void readsOnlyTheRequestedDaysEvenIfTheSourceAnswersMore() {
    given(fundValueQueries.findValuesBetweenDates(INDEX, FROM, TO))
        .willReturn(
            List.of(
                aFundValue(INDEX, LocalDate.of(2026, 2, 24), 199.0),
                aFundValue(INDEX, LocalDate.of(2026, 2, 25), 200.0),
                aFundValue(INDEX, LocalDate.of(2026, 3, 4), 205.0)));

    var spliced = series.closes(List.of(new SeriesSegment(INDEX, null)), FROM, TO);

    assertThat(spliced).extracting(FundValue::date).containsExactly(LocalDate.of(2026, 2, 25));
  }

  @Test
  void refusesALaterSegmentWithNoCloseOnThePreviousSegmentsLastClose() {
    given(fundValueQueries.findValuesBetweenDates(INDEX, FROM, LocalDate.of(2026, 2, 28)))
        .willReturn(List.of(aFundValue(INDEX, LocalDate.of(2026, 2, 27), 204.0)));
    given(fundValueQueries.findValuesBetweenDates(OWN_NAV, LocalDate.of(2026, 2, 27), TO))
        .willReturn(List.of(aFundValue(OWN_NAV, LocalDate.of(2026, 3, 2), 1.01)));

    assertThatThrownBy(() -> series.closes(INDEX_THEN_OWN_NAV, FROM, TO))
        .isInstanceOf(SeriesSpliceRefusedException.class);
  }

  @Test
  void refusesToContinueFromAPreviousSegmentWhoseLastCloseIsNotPositive() {
    given(fundValueQueries.findValuesBetweenDates(INDEX, FROM, LocalDate.of(2026, 2, 28)))
        .willReturn(
            List.of(
                aFundValue(INDEX, LocalDate.of(2026, 2, 26), 202.0),
                aFundValue(INDEX, LocalDate.of(2026, 2, 27), BigDecimal.ZERO)));

    assertThatThrownBy(() -> series.closes(INDEX_THEN_OWN_NAV, FROM, TO))
        .isInstanceOf(SeriesSpliceRefusedException.class);
    verify(fundValueQueries, never()).findValuesBetweenDates(eq(OWN_NAV), any(), any());
  }

  @Test
  void refusesALaterSegmentWhoseCloseOnThePreviousSegmentsLastCloseIsNotPositive() {
    given(fundValueQueries.findValuesBetweenDates(INDEX, FROM, LocalDate.of(2026, 2, 28)))
        .willReturn(List.of(aFundValue(INDEX, LocalDate.of(2026, 2, 27), 204.0)));
    given(fundValueQueries.findValuesBetweenDates(OWN_NAV, LocalDate.of(2026, 2, 27), TO))
        .willReturn(
            List.of(
                aFundValue(OWN_NAV, LocalDate.of(2026, 2, 27), BigDecimal.ZERO),
                aFundValue(OWN_NAV, LocalDate.of(2026, 3, 2), 1.01)));

    assertThatThrownBy(() -> series.closes(INDEX_THEN_OWN_NAV, FROM, TO))
        .isInstanceOf(SeriesSpliceRefusedException.class);
  }

  @Test
  void aSegmentThatEndsBeforeTheWindowIsNotQueriedAndTheNextOneStartsAtItsOwnLevel() {
    var windowAfterTheSplice = LocalDate.of(2026, 3, 2);
    var ownCloses =
        List.of(
            aFundValue(OWN_NAV, LocalDate.of(2026, 3, 2), 1.01),
            aFundValue(OWN_NAV, LocalDate.of(2026, 3, 3), 0.99));
    given(fundValueQueries.findValuesBetweenDates(OWN_NAV, windowAfterTheSplice, TO))
        .willReturn(ownCloses);

    var spliced = series.closes(INDEX_THEN_OWN_NAV, windowAfterTheSplice, TO);

    verify(fundValueQueries, never()).findValuesBetweenDates(eq(INDEX), any(), any());
    assertThat(spliced)
        .extracting(FundValue::value)
        .containsExactly(BigDecimal.valueOf(1.01), BigDecimal.valueOf(0.99));
  }

  @Test
  void aWindowEndingBeforeTheSpliceReadsOnlyTheEarlierSegment() {
    var beforeTheSplice = LocalDate.of(2026, 2, 27);
    given(fundValueQueries.findValuesBetweenDates(INDEX, FROM, beforeTheSplice))
        .willReturn(List.of(aFundValue(INDEX, LocalDate.of(2026, 2, 27), 204.0)));

    var spliced = series.closes(INDEX_THEN_OWN_NAV, FROM, beforeTheSplice);

    verify(fundValueQueries, never()).findValuesBetweenDates(eq(OWN_NAV), any(), any());
    assertThat(spliced).extracting(FundValue::date).containsExactly(beforeTheSplice);
  }

  @Test
  void theLastCloseIsTheLastSegmentsLatestClose() {
    given(fundValueQueries.findLastValueForFund(OWN_NAV))
        .willReturn(Optional.of(aFundValue(OWN_NAV, LocalDate.of(2026, 11, 11), 1.15)));

    assertThat(series.lastCloseDate(INDEX_THEN_OWN_NAV)).contains(LocalDate.of(2026, 11, 11));
  }

  @Test
  void theSeriesKeyNamesEverySegmentInOrder() {
    assertThat(SplicedPriceSeries.key(INDEX_THEN_OWN_NAV)).isEqualTo("MSCI_ACWI+EE0000003283");
  }
}
