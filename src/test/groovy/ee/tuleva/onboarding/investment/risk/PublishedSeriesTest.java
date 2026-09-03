package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.CHANGE_CONFIRMED;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.CHANGE_PENDING;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.STABLE;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRRI;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ONE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.risk.PublishedSeries.PublishedPoint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublishedSeriesTest {

  private static final LocalDate START = LocalDate.of(2021, 1, 1);

  @Test
  void publishedSinceIsTheStartOfTheRunNotTheEdgeOfTheAnalysisLookback() {
    var classes = sameClass(200, 4);

    var indicator = analyse(classes, classes);

    assertThat(indicator.publishedSince()).isEqualTo(START);
    assertThat(indicator.publishedSinceIsTruncated()).isTrue();
    assertThat(indicator.streakReferencePoints()).isEqualTo(200);
    assertThat(indicator.previousPublishedClass()).isNull();
  }

  @Test
  void aChangeOlderThanTwentyFourMonthsNoLongerReportsThePreviousClass() {
    var classes = new ArrayList<>(sameClass(10, 3));
    classes.addAll(sameClass(150, 4));

    var indicator = analyse(classes, classes);

    assertThat(indicator.previousPublishedClass()).isNull();
    assertThat(indicator.status()).isEqualTo(STABLE);
  }

  @Test
  void aChangeWithinFourMonthsIsConfirmedAndKeepsThePreviousClass() {
    var classes = new ArrayList<>(sameClass(150, 4));
    classes.addAll(sameClass(10, 5));

    var indicator = analyse(classes, classes);

    assertThat(indicator.previousPublishedClass()).isEqualTo(4);
    assertThat(indicator.publishedClass()).isEqualTo(5);
    assertThat(indicator.status()).isEqualTo(CHANGE_CONFIRMED);
  }

  @Test
  void aChangeConfirmedEighteenMonthsAgoIsStableAgain() {
    var classes = new ArrayList<>(sameClass(20, 4));
    classes.addAll(sameClass(80, 5));

    var indicator = analyse(classes, classes);

    assertThat(indicator.previousPublishedClass()).isEqualTo(4);
    assertThat(indicator.status()).isEqualTo(STABLE);
  }

  @Test
  void aRawClassDifferentFromThePublishedClassIsPending() {
    var published = sameClass(160, 4);
    var raw = new ArrayList<>(sameClass(151, 4));
    raw.addAll(sameClass(9, 5));

    var indicator = analyse(raw, published);

    assertThat(indicator.publishedClass()).isEqualTo(4);
    assertThat(indicator.rawLatestClass()).isEqualTo(5);
    assertThat(indicator.status()).isEqualTo(CHANGE_PENDING);
    assertThat(indicator.rawStreakReferencePoints()).isEqualTo(9);
    assertThat(indicator.rawClassSince()).isEqualTo(START.plusWeeks(151));
  }

  @Test
  void telemetryCountsTheRawPointsOfTheLastFourMonths() {
    var published = sameClass(160, 4);
    var raw = new ArrayList<>(sameClass(155, 4));
    raw.addAll(sameClass(5, 5));

    var indicator = analyse(raw, published);

    assertThat(indicator.windowReferencePoints()).isEqualTo(18);
    assertThat(indicator.matchingReferencePoints()).isEqualTo(5);
  }

  @Test
  void unclassifiedPointsWithinTheTelemetryWindowAreNotCounted() {
    var published = sameClass(160, 4);
    var raw = new ArrayList<>(sameClass(155, 4));
    raw.add(null);
    raw.addAll(sameClass(4, 4));

    var indicator = analyse(raw, published);

    assertThat(indicator.windowReferencePoints()).isEqualTo(17);
    assertThat(indicator.matchingReferencePoints()).isEqualTo(17);
  }

  @Test
  void unclassifiedRawPointsAreExcludedFromTheAnalysis() {
    var raw = new ArrayList<Integer>();
    raw.add(null);
    raw.addAll(sameClass(19, 4));

    var indicator = analyse(raw, sameClass(20, 4));

    assertThat(indicator.rawStreakReferencePoints()).isEqualTo(19);
    assertThat(indicator.latestObservationCount()).isEqualTo(260);
  }

  private static PublishedRiskIndicator analyse(
      List<Integer> rawClasses, List<Integer> publishedClasses) {
    var published = new ArrayList<PublishedPoint>();
    for (int i = 0; i < publishedClasses.size(); i++) {
      published.add(new PublishedPoint(START.plusWeeks(i), publishedClasses.get(i)));
    }
    var raw = new ArrayList<ReferencePoint>();
    for (int i = 0; i < rawClasses.size(); i++) {
      raw.add(new ReferencePoint(START.plusWeeks(i), rawClasses.get(i), 260, ONE, Map.of()));
    }
    return new PublishedSeries(published).analyse(TUK75, SRRI, raw);
  }

  private static List<Integer> sameClass(int count, int riskClass) {
    var classes = new ArrayList<Integer>(count);
    for (int i = 0; i < count; i++) {
      classes.add(riskClass);
    }
    return classes;
  }
}
