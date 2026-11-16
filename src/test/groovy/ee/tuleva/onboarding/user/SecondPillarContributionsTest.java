package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status.CONTRIBUTING;
import static ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status.CONTRIBUTING_WITHOUT_ACCOUNT;
import static ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status.NOT_CONTRIBUTING;
import static ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status.PAUSED;
import static ee.tuleva.onboarding.user.SecondPillarExit.MONEY_WITHDRAWN;
import static ee.tuleva.onboarding.user.SecondPillarExit.PAYMENTS_STOPPED;
import static ee.tuleva.onboarding.user.UserContactsFixture.contributionPeriod;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import ee.tuleva.onboarding.auth.principal.Person;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class SecondPillarContributionsTest {

  private static final Person PERSON = samplePerson();

  private final SecondPillarContributions contributionsOn27October2026 = on("2026-10-27T10:00:00Z");

  @ParameterizedTest
  @CsvSource({
    "2021-08-31, 2021-09-01, 2026-11-01",
    "2021-12-31, 2022-01-01, 2026-11-01",
    "2022-04-30, 2022-05-01, 2026-11-01",
    "2022-08-31, 2022-09-01, 2026-12-01",
    "2022-12-31, 2023-01-01, 2027-04-01",
    "2023-04-30, 2023-05-01, 2027-08-01",
    "2023-08-31, 2023-09-01, 2027-12-01",
  })
  void aLeaverMayFileInTheWindowPensionikeskusPublishesForTheDayContributionsStopped(
      String lastContribution, String stopped, String filingOpens) {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", lastContribution),
            contributionPeriod(NOT_CONTRIBUTING, stopped));

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods))
        .isEqualTo(stoppedAndMayRejoinFrom(filingOpens));
  }

  @Test
  void aLeaverWhoseFiveYearsPassedBeforeTheRuleTookEffectMayRejoinFromTheDayItTakesEffect() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01"));

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods))
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

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods))
        .isEqualTo(stoppedAndMayRejoinFrom("2026-11-01"));
  }

  @Test
  void aLeaverWhoseLastPeriodHasEndedMayFileNineMonthsBeforeFiveYearsAfterTheLastContribution() {
    var periods = List.of(contributionPeriod(CONTRIBUTING, "2002-07-01", "2023-08-31"));

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods))
        .isEqualTo(stoppedAndMayRejoinFrom("2027-12-01"));
  }

  @Test
  void aLeaverWhoseContributionsEndedTwiceMayFileNineMonthsBeforeFiveYearsAfterTheLaterEnd() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING_WITHOUT_ACCOUNT, "2015-01-01", "2024-04-30"),
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"));

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods))
        .isEqualTo(stoppedAndMayRejoinFrom("2028-08-01"));
  }

  @Test
  void
      anUpcomingContributionThatAStopOverlaysDoesNotMoveTheRejoinDateOfALeaverWithNoPeriodInEffect() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2023-08-31"),
            contributionPeriod(CONTRIBUTING, "2027-05-01", "2027-08-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2027-05-01", "2027-08-31"));

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods))
        .isEqualTo(stoppedAndMayRejoinFrom("2027-12-01"));
  }

  @Test
  void aSaverWhoPausedIn2020And2021AndKeptSavingIsContributingOnceThePauseIsOver() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01"),
            contributionPeriod(PAUSED, "2020-12-01", "2021-08-31"));

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods)).isEqualTo(contributing());
  }

  @Test
  void aSaverIsNotContributingWhilePausedAndHasNoRejoinDateSinceTheyNeverLeft() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01"),
            contributionPeriod(PAUSED, "2020-12-01", "2021-08-31"));

    assertThat(on("2021-03-15T10:00:00Z").statusOf(PERSON, periods))
        .isEqualTo(pausedWithoutRejoinDate());
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
                .map(periods -> contributionsOn27October2026.statusOf(PERSON, periods))
                .toList())
        .containsExactly(
            pausedWithoutRejoinDate(),
            stoppedAndMayRejoinFrom("2030-12-01"),
            pausedWithoutRejoinDate());
  }

  @Test
  void aRejoinerIsNotContributingUntilTheProcessedRejoinStartsAndKnowsWhenItStarts() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01"),
            contributionPeriod(CONTRIBUTING, "2027-05-01"));

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods))
        .isEqualTo(notContributingUntil("2027-05-01"));
  }

  @Test
  void aRejoinerIsContributingFromTheFirstMinuteOfTheRejoinStartDayInTallinn() {
    var periods =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01"),
            contributionPeriod(CONTRIBUTING, "2027-05-01"));

    assertThat(on("2027-04-30T21:00:00Z").statusOf(PERSON, periods)).isEqualTo(contributing());
  }

  @Test
  void aStopStaysInEffectOnItsEndDateAndContributionsRestartTheNextDay() {
    var periods =
        List.of(
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01", "2026-10-27"),
            contributionPeriod(CONTRIBUTING, "2026-10-28"));

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods))
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

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods))
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

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods))
        .isEqualTo(notContributingUntil("2027-09-01"));
  }

  @Test
  void aMandatoryJoinerWithoutAPensionAccountIsContributing() {
    var periods = List.of(contributionPeriod(CONTRIBUTING_WITHOUT_ACCOUNT, "2025-01-01"));

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods)).isEqualTo(contributing());
  }

  @Test
  void contributingIsUnknownWithoutContributionPeriods() {
    assertThat(contributionsOn27October2026.statusOf(PERSON, List.of())).isNull();
  }

  @Test
  void contributingIsUnknownWhileNoPeriodHasStartedYet() {
    var periods = List.of(contributionPeriod(CONTRIBUTING, "2027-01-01"));

    assertThat(contributionsOn27October2026.statusOf(PERSON, periods)).isNull();
  }

  @ParameterizedTest
  @MethodSource
  void aLeaverMayRejoinTodayOnlyOnceTheRejoinDateHasComeAndNoRestartIsComing(
      List<SecondPillarContributionPeriod> periods, boolean mayRejoin) {
    var on10November2026 = on("2026-11-10T10:00:00Z");

    assertThat(on10November2026.mayRejoinToday(PERSON, periods)).isEqualTo(mayRejoin);
  }

  private static Stream<Arguments>
      aLeaverMayRejoinTodayOnlyOnceTheRejoinDateHasComeAndNoRestartIsComing() {
    return Stream.of(
        arguments(named("stopped in 2012", stoppedOn("2011-12-31", "2012-01-01")), true),
        arguments(named("stopped in May 2022", stoppedOn("2022-04-30", "2022-05-01")), true),
        arguments(
            named(
                "stopped in 2012 and rejoined from May 2027",
                List.of(
                    contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"),
                    contributionPeriod(NOT_CONTRIBUTING, "2012-01-01"),
                    contributionPeriod(CONTRIBUTING, "2027-05-01"))),
            false),
        arguments(named("stopped in September 2023", stoppedOn("2023-08-31", "2023-09-01")), false),
        arguments(
            named("still contributing", List.of(contributionPeriod(CONTRIBUTING, "2002-07-01"))),
            false),
        arguments(named("no contribution periods", List.of()), false));
  }

  @Test
  void nobodyMayRejoinBeforeTheFiveYearRuleTakesEffect() {
    assertThat(
            contributionsOn27October2026.mayRejoinToday(
                PERSON, stoppedOn("2011-12-31", "2012-01-01")))
        .isFalse();
  }

  @Test
  void someoneWhoOnlyStoppedPaymentsMayRejoinFromTheStartOf2028InsteadOfAfterFiveYears() {
    var contributions = on("2026-11-16T10:00:00Z", person -> PAYMENTS_STOPPED);

    assertThat(contributions.statusOf(PERSON, stoppedOn("2024-08-31", "2024-09-01")))
        .isEqualTo(stoppedAndMayRejoinFrom("2028-01-01"));
  }

  @Test
  void someoneWhoWithdrewMoneyStillWaitsFiveYearsAfterTheirContributionsEnded() {
    var contributions = on("2026-11-16T10:00:00Z", person -> MONEY_WITHDRAWN);

    assertThat(contributions.statusOf(PERSON, stoppedOn("2024-08-31", "2024-09-01")))
        .isEqualTo(stoppedAndMayRejoinFrom("2028-12-01"));
  }

  @Test
  void howSomeoneLeftIsNotLookedUpWhenTheirFiveYearsEndBefore2028() {
    var contributions =
        on(
            "2026-11-16T10:00:00Z",
            person -> {
              throw new AssertionError("looked up how the person left");
            });

    assertThat(contributions.statusOf(PERSON, stoppedOn("2022-08-31", "2022-09-01")))
        .isEqualTo(stoppedAndMayRejoinFrom("2026-12-01"));
  }

  @Test
  void someoneWhoStopsPaymentsAfter2028MayRejoinFromTheDayTheirContributionsStop() {
    var contributions = on("2029-02-01T10:00:00Z", person -> PAYMENTS_STOPPED);

    assertThat(contributions.statusOf(PERSON, stoppedOn("2028-12-31", "2029-01-01")))
        .isEqualTo(stoppedAndMayRejoinFrom("2029-01-01"));
  }

  @Test
  void someoneWhoOnlyStoppedPaymentsMayRejoinOnTheFirstDayOf2028() {
    var contributions = on("2028-01-01T08:00:00Z", person -> PAYMENTS_STOPPED);

    assertThat(contributions.mayRejoinToday(PERSON, stoppedOn("2025-08-31", "2025-09-01")))
        .isTrue();
  }

  private static List<SecondPillarContributionPeriod> stoppedOn(
      String lastContribution, String stopped) {
    return List.of(
        contributionPeriod(CONTRIBUTING, "2002-07-01", lastContribution),
        contributionPeriod(NOT_CONTRIBUTING, stopped));
  }

  private static SecondPillarContributions on(String instant) {
    return on(instant, person -> MONEY_WITHDRAWN);
  }

  private static SecondPillarContributions on(String instant, SecondPillarExits exits) {
    return new SecondPillarContributions(Clock.fixed(Instant.parse(instant), UTC), exits);
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
