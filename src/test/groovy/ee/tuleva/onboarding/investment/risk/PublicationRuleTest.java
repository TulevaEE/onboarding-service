package ee.tuleva.onboarding.investment.risk;

import static java.math.BigDecimal.ONE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublicationRuleTest {

  private static final LocalDate START = LocalDate.of(2024, 1, 1);

  private final MajorityPublicationRule majority = new MajorityPublicationRule();
  private final PersistencePublicationRule persistence = new PersistencePublicationRule();

  @Test
  void majorityKeepsASingleClassSeriesUnchanged() {
    var series = majority.publish(daily(sameClass(200, 4)));

    assertThat(distinctPublished(series)).containsExactly(4);
  }

  @Test
  void majorityFlipsOnceTheNewClassHoldsMoreThanHalfOfTheWindow() {
    var classes = new ArrayList<>(sameClass(200, 4));
    classes.addAll(sameClass(200, 5));

    var series = majority.publish(daily(classes));

    assertThat(series.points().getLast().riskClass()).isEqualTo(5);
    assertThat(series.points().get(210).riskClass()).isEqualTo(4);
  }

  @Test
  void majorityIgnoresASingleBlip() {
    var classes = new ArrayList<>(sameClass(200, 4));
    classes.add(5);
    classes.addAll(sameClass(50, 4));

    var series = majority.publish(daily(classes));

    assertThat(distinctPublished(series)).containsExactly(4);
  }

  @Test
  void majorityCarriesTheLastClassForwardWhenNoClassHoldsAMajority() {
    var classes = new ArrayList<Integer>();
    for (int i = 0; i < 300; i++) {
      classes.add(4 + i % 3);
    }

    var series = majority.publish(daily(classes));

    assertThat(distinctPublished(series)).containsExactly(4);
  }

  @Test
  void bothRulesIgnoreUnclassifiedPoints() {
    var points = new ArrayList<ReferencePoint>();
    points.add(new ReferencePoint(START, null, 10, ONE, Map.of()));
    for (int week = 1; week <= 25; week++) {
      points.add(new ReferencePoint(START.plusWeeks(week), 5, 260, ONE, Map.of()));
    }

    assertThat(majority.publish(points).points())
        .isNotEmpty()
        .extracting(PublishedSeries.PublishedPoint::date)
        .doesNotContain(START);
    assertThat(persistence.publish(points).points())
        .isNotEmpty()
        .extracting(PublishedSeries.PublishedPoint::date)
        .doesNotContain(START);
    assertThat(distinctPublished(persistence.publish(points))).containsExactly(5);
  }

  @Test
  void neitherRulePublishesAnythingWhileNoPointCarriesAClass() {
    var points =
        List.of(
            new ReferencePoint(START, null, 10, ONE, Map.of()),
            new ReferencePoint(START.plusWeeks(1), null, 12, ONE, Map.of()));

    assertThat(majority.publish(points).points()).isEmpty();
    assertThat(persistence.publish(points).points()).isEmpty();
  }

  @Test
  void anUnclassifiedPointDoesNotBecomeAPublishedEntry() {
    var points = new ArrayList<ReferencePoint>();
    for (int week = 0; week <= 35; week++) {
      var date = START.plusWeeks(week);
      points.add(week == 30 ? new ReferencePoint(date, null, 260, ONE, Map.of()) : point(date, 4));
    }

    var series = persistence.publish(points);

    assertThat(series.points())
        .extracting(PublishedSeries.PublishedPoint::date)
        .doesNotContain(START.plusWeeks(30));
  }

  @Test
  void persistenceFlipsToThePrevailingClassAfterFourMonthsFullyOutsideTheCurrentClass() {
    var classes = new ArrayList<>(sameClass(30, 4));
    for (int i = 0; i < 30; i++) {
      classes.add(i % 3 == 0 ? 6 : 5);
    }

    var series = persistence.publish(weekly(classes));

    assertThat(series.points().getLast().riskClass()).isEqualTo(5);
  }

  @Test
  void persistenceFlipsAfterFourUninterruptedMonthsOutsideTheCurrentClass() {
    var classes = new ArrayList<>(sameClass(30, 4));
    classes.addAll(sameClass(20, 5));

    var series = persistence.publish(weekly(classes));

    assertThat(series.points().getLast().riskClass()).isEqualTo(5);
  }

  @Test
  void oneReturnToTheCurrentClassRestartsThePersistenceClock() {
    var classes = new ArrayList<>(sameClass(30, 4));
    for (int i = 0; i < 20; i++) {
      classes.add(i == 10 ? 4 : 5);
    }

    var series = persistence.publish(weekly(classes));

    assertThat(distinctPublished(series)).containsExactly(4);
  }

  @Test
  void persistenceDoesNotFlipJustUnderFourMonths() {
    var classes = new ArrayList<>(sameClass(30, 4));
    classes.addAll(sameClass(16, 5));

    var series = persistence.publish(weekly(classes));

    assertThat(distinctPublished(series)).containsExactly(4);
  }

  @Test
  void persistencePicksTheHigherClassWhenTwoClassesTie() {
    var classes = new ArrayList<>(sameClass(30, 4));
    for (int i = 0; i < 40; i++) {
      classes.add(i % 2 == 0 ? 5 : 6);
    }

    var series = persistence.publish(weekly(classes));

    assertThat(series.points().getLast().riskClass()).isEqualTo(6);
  }

  @Test
  void aSingleMissingWeekDoesNotSuspendTheMigrationAssessment() {
    var series = persistence.publish(migratingSeriesWithoutWeeks(40));

    assertThat(series.points().getLast().riskClass()).isEqualTo(5);
  }

  @Test
  void aWiderHoleInTheWindowSuspendsTheMigrationAssessment() {
    var series = persistence.publish(migratingSeriesWithoutWeeks(40, 44));

    assertThat(distinctPublished(series)).containsExactly(4);
  }

  private static List<ReferencePoint> migratingSeriesWithoutWeeks(Integer... missingWeeks) {
    var missing = Set.of(missingWeeks);
    var points = new ArrayList<ReferencePoint>();
    for (int week = 0; week < 50; week++) {
      if (!missing.contains(week)) {
        points.add(point(START.plusWeeks(week), week < 30 ? 4 : 5));
      }
    }
    return points;
  }

  @Test
  void aPublicHolidayShiftingTheWeekEndDoesNotCountAsAMissingWeek() {
    var points = new ArrayList<ReferencePoint>();
    for (int i = 0; i < 30; i++) {
      points.add(point(friday(i), 4));
    }
    for (int i = 30; i < 50; i++) {
      points.add(point(i == 40 ? friday(i).minusDays(1) : friday(i), 5));
    }

    var series = persistence.publish(points);

    assertThat(series.points().getLast().riskClass()).isEqualTo(5);
  }

  private static LocalDate friday(int week) {
    return START.plusWeeks(week).plusDays(4);
  }

  @Test
  void persistencePublishesNothingBeforeFourMonthsOfHistoryExist() {
    var series = persistence.publish(weekly(sameClass(8, 5)));

    assertThat(series.points()).isEmpty();
  }

  @Test
  void theFirstPublishedClassComesFromAWholeWindowNotFromTheOldestStoredPoint() {
    var classes = new ArrayList<Integer>();
    classes.add(6);
    classes.addAll(sameClass(29, 5));

    var series = persistence.publish(weekly(classes));

    assertThat(distinctPublished(series)).containsExactly(5);
  }

  private static List<Integer> sameClass(int count, int riskClass) {
    var classes = new ArrayList<Integer>(count);
    for (int i = 0; i < count; i++) {
      classes.add(riskClass);
    }
    return classes;
  }

  private static List<ReferencePoint> daily(List<Integer> classes) {
    var points = new ArrayList<ReferencePoint>(classes.size());
    var date = START;
    for (var riskClass : classes) {
      points.add(point(date, riskClass));
      date = date.plusDays(1);
    }
    return points;
  }

  private static List<ReferencePoint> weekly(List<Integer> classes) {
    var points = new ArrayList<ReferencePoint>(classes.size());
    var date = START;
    for (var riskClass : classes) {
      points.add(point(date, riskClass));
      date = date.plusWeeks(1);
    }
    return points;
  }

  private static ReferencePoint point(LocalDate date, Integer riskClass) {
    return new ReferencePoint(date, riskClass, 260, ONE, Map.of());
  }

  private static List<Integer> distinctPublished(PublishedSeries series) {
    return series.points().stream()
        .map(PublishedSeries.PublishedPoint::riskClass)
        .distinct()
        .toList();
  }
}
