package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status.CONTRIBUTING;
import static ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status.CONTRIBUTING_WITHOUT_ACCOUNT;
import static ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status.NOT_CONTRIBUTING;
import static ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status.PAUSED;
import static ee.tuleva.onboarding.user.UserContactsFixture.contributionPeriod;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SecondPillarContributionsTest {

  private final SecondPillarContributions contributionsOn27October2026 = on("2026-10-27T10:00:00Z");

  @Test
  void aLeaverWhoStoppedContributionsMayRejoinFiveYearsAfterTheStopBegan() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2023-08-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2023-09-01"));

    assertThat(contributionsOn27October2026.statusOf(periods))
        .isEqualTo(stoppedAndMayRejoinFrom("2028-09-01"));
  }

  @Test
  void aLeaverWhoseFiveYearsPassedBeforeTheRuleTookEffectMayRejoinFromTheDayItTakesEffect() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01"));

    assertThat(contributionsOn27October2026.statusOf(periods))
        .isEqualTo(stoppedAndMayRejoinFrom("2026-11-01"));
  }

  @Test
  void
      aSaverWhoTookTheMoneyOutIn2021MayRejoinFromTheDayTheFiveYearRuleTakesEffectAlthoughAPauseOverlapsTheLastSaving() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2021-08-31"),
            contributionPeriod(PAUSED, "2020-12-01", "2021-08-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2021-09-01"));

    assertThat(contributionsOn27October2026.statusOf(periods))
        .isEqualTo(stoppedAndMayRejoinFrom("2026-11-01"));
  }

  @Test
  void aLeaverWhoseLastPeriodHasEndedMayRejoinFiveYearsAfterTheDayAfterTheLastContribution() {
    var periods = List.of(contributionPeriod(CONTRIBUTING, "2002-07-01", "2023-08-31"));

    assertThat(contributionsOn27October2026.statusOf(periods))
        .isEqualTo(stoppedAndMayRejoinFrom("2028-09-01"));
  }

  @Test
  void aLeaverWhoseContributionsEndedTwiceMayRejoinFiveYearsAfterTheLaterEnd() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING_WITHOUT_ACCOUNT, "2015-01-01", "2024-04-30"),
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"));

    assertThat(contributionsOn27October2026.statusOf(periods))
        .isEqualTo(stoppedAndMayRejoinFrom("2029-05-01"));
  }

  @Test
  void
      anUpcomingContributionThatAStopOverlaysDoesNotMoveTheRejoinDateOfALeaverWithNoPeriodInEffect() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2023-08-31"),
            contributionPeriod(CONTRIBUTING, "2027-05-01", "2027-08-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2027-05-01", "2027-08-31"));

    assertThat(contributionsOn27October2026.statusOf(periods))
        .isEqualTo(stoppedAndMayRejoinFrom("2028-09-01"));
  }

  @Test
  void aSaverWhoPausedIn2020And2021AndKeptSavingIsContributingOnceThePauseIsOver() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01"),
            contributionPeriod(PAUSED, "2020-12-01", "2021-08-31"));

    assertThat(contributionsOn27October2026.statusOf(periods)).isEqualTo(contributing());
  }

  @Test
  void aSaverIsNotContributingWhilePausedAndHasNoRejoinDateSinceTheyNeverLeft() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01"),
            contributionPeriod(PAUSED, "2020-12-01", "2021-08-31"));

    assertThat(on("2021-03-15T10:00:00Z").statusOf(periods)).isEqualTo(pausedWithoutRejoinDate());
  }

  @Test
  void
      aStopOrPauseStartingOnTheSameDayAsContributionsOverlaysThemWhicheverRowTheRegistryListsFirst() {
    var contributionsListedBeforeAPause =
        List.of(
            contributionPeriod(CONTRIBUTING, "2026-09-01"),
            contributionPeriod(PAUSED, "2026-09-01", "2027-08-31"));
    var contributionsListedBeforeAStop =
        List.of(
            contributionPeriod(CONTRIBUTING_WITHOUT_ACCOUNT, "2026-09-01"),
            contributionPeriod(NOT_CONTRIBUTING, "2026-09-01"));
    var aPauseListedBeforeContributions =
        List.of(
            contributionPeriod(PAUSED, "2026-09-01", "2027-08-31"),
            contributionPeriod(CONTRIBUTING, "2026-09-01"));

    assertThat(
            Stream.of(
                    contributionsListedBeforeAPause,
                    contributionsListedBeforeAStop,
                    aPauseListedBeforeContributions)
                .map(contributionsOn27October2026::statusOf)
                .toList())
        .containsExactly(
            pausedWithoutRejoinDate(),
            stoppedAndMayRejoinFrom("2031-09-01"),
            pausedWithoutRejoinDate());
  }

  @Test
  void aRejoinerIsNotContributingUntilTheProcessedRejoinStartsAndKnowsWhenItStarts() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01"),
            contributionPeriod(CONTRIBUTING, "2027-05-01"));

    assertThat(contributionsOn27October2026.statusOf(periods))
        .isEqualTo(notContributingUntil("2027-05-01"));
  }

  @Test
  void aRejoinerIsContributingFromTheFirstMinuteOfTheRejoinStartDayInTallinn() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01"),
            contributionPeriod(CONTRIBUTING, "2027-05-01"));

    assertThat(on("2027-04-30T21:00:00Z").statusOf(periods)).isEqualTo(contributing());
  }

  @Test
  void aStopStaysInEffectOnItsEndDateAndContributionsRestartTheNextDay() {
    var periods =
        List.of(
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01", "2026-10-27"),
            contributionPeriod(CONTRIBUTING, "2026-10-28"));

    assertThat(contributionsOn27October2026.statusOf(periods))
        .isEqualTo(notContributingUntil("2026-10-28"));
  }

  @Test
  void theRestartDateIsTheEarliestUpcomingPeriodThatMakesContributions() {
    var periods =
        List.of(
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01"),
            contributionPeriod(CONTRIBUTING, "2028-01-01"),
            contributionPeriod(PAUSED, "2027-01-01", "2027-03-31"),
            contributionPeriod(CONTRIBUTING_WITHOUT_ACCOUNT, "2027-05-01", "2027-12-31"));

    assertThat(contributionsOn27October2026.statusOf(periods))
        .isEqualTo(notContributingUntil("2027-05-01"));
  }

  @Test
  void anUpcomingContributionThatAStopStartingTheSameDayOverlaysIsNoRestart() {
    var periods =
        List.of(
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01", "2027-04-30"),
            contributionPeriod(CONTRIBUTING, "2027-05-01", "2027-08-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2027-05-01", "2027-08-31"),
            contributionPeriod(CONTRIBUTING, "2027-09-01"));

    assertThat(contributionsOn27October2026.statusOf(periods))
        .isEqualTo(notContributingUntil("2027-09-01"));
  }

  @Test
  void aMandatoryJoinerWithoutAPensionAccountIsContributing() {
    var periods = List.of(contributionPeriod(CONTRIBUTING_WITHOUT_ACCOUNT, "2025-01-01"));

    assertThat(contributionsOn27October2026.statusOf(periods)).isEqualTo(contributing());
  }

  @Test
  void contributingIsUnknownWithoutContributionPeriods() {
    assertThat(contributionsOn27October2026.statusOf(List.of())).isNull();
  }

  @Test
  void contributingIsUnknownWhileNoPeriodHasStartedYet() {
    var periods = List.of(contributionPeriod(CONTRIBUTING, "2027-01-01"));

    assertThat(contributionsOn27October2026.statusOf(periods)).isNull();
  }

  private static SecondPillarContributions on(String instant) {
    return new SecondPillarContributions(Clock.fixed(Instant.parse(instant), UTC));
  }

  private static SecondPillarContributionStatus contributing() {
    return new SecondPillarContributionStatus(true, null, null);
  }

  private static SecondPillarContributionStatus pausedWithoutRejoinDate() {
    return new SecondPillarContributionStatus(false, null, null);
  }

  private static SecondPillarContributionStatus notContributingUntil(String restartDate) {
    return new SecondPillarContributionStatus(false, LocalDate.parse(restartDate), null);
  }

  private static SecondPillarContributionStatus stoppedAndMayRejoinFrom(String rejoinPossibleFrom) {
    return new SecondPillarContributionStatus(false, null, LocalDate.parse(rejoinPossibleFrom));
  }
}
