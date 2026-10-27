package ee.tuleva.onboarding.user;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SecondPillarContributions {

  private final Clock clock;

  @Nullable SecondPillarContributionStatus statusOf(List<SecondPillarContributionPeriod> periods) {
    final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
    LocalDate today = LocalDate.now(clock.withZone(ESTONIAN_ZONE));
    if (periods.stream().noneMatch(period -> period.hasStartedBy(today))) {
      return null;
    }
    if (makesContributionsOn(today, periods)) {
      return new SecondPillarContributionStatus(true, null, null);
    }
    LocalDate restartDate = restartDateAfter(today, periods);
    if (restartDate != null) {
      return new SecondPillarContributionStatus(false, restartDate, null);
    }
    return new SecondPillarContributionStatus(false, null, rejoinPossibleFrom(today, periods));
  }

  private static Optional<SecondPillarContributionPeriod> decidingPeriodOn(
      LocalDate day, List<SecondPillarContributionPeriod> periods) {
    return periods.stream()
        .filter(period -> period.isInEffectOn(day))
        .max(
            comparing(SecondPillarContributionPeriod::startDate)
                .thenComparing(SecondPillarContributionPeriod::overlaysContributions));
  }

  private static boolean makesContributionsOn(
      LocalDate day, List<SecondPillarContributionPeriod> periods) {
    return decidingPeriodOn(day, periods)
        .map(SecondPillarContributionPeriod::makesContributions)
        .orElse(false);
  }

  private static @Nullable LocalDate restartDateAfter(
      LocalDate day, List<SecondPillarContributionPeriod> periods) {
    return periods.stream()
        .filter(period -> period.startsContributingAfter(day))
        .map(SecondPillarContributionPeriod::startDate)
        .filter(startDate -> makesContributionsOn(startDate, periods))
        .min(naturalOrder())
        .orElse(null);
  }

  private static @Nullable LocalDate rejoinPossibleFrom(
      LocalDate day, List<SecondPillarContributionPeriod> periods) {
    final Period FUNDED_PENSIONS_ACT_27_2_LG_1_LEAVER_REJOINS_AFTER_CONTRIBUTIONS_ENDED =
        Period.ofYears(5);
    final LocalDate FUNDED_PENSIONS_ACT_27_2_LG_1_FIVE_YEAR_RULE_IN_FORCE_FROM =
        LocalDate.of(2026, 11, 1);
    return stopStartDate(day, periods)
        .map(
            stopStart ->
                stopStart.plus(
                    FUNDED_PENSIONS_ACT_27_2_LG_1_LEAVER_REJOINS_AFTER_CONTRIBUTIONS_ENDED))
        .map(
            fiveYearsLater ->
                fiveYearsLater.isAfter(FUNDED_PENSIONS_ACT_27_2_LG_1_FIVE_YEAR_RULE_IN_FORCE_FROM)
                    ? fiveYearsLater
                    : FUNDED_PENSIONS_ACT_27_2_LG_1_FIVE_YEAR_RULE_IN_FORCE_FROM)
        .orElse(null);
  }

  private static Optional<LocalDate> stopStartDate(
      LocalDate day, List<SecondPillarContributionPeriod> periods) {
    Optional<SecondPillarContributionPeriod> decidingPeriod = decidingPeriodOn(day, periods);
    if (decidingPeriod.isEmpty()) {
      return dayAfterContributionsEndedBefore(day, periods);
    }
    return decidingPeriod
        .filter(SecondPillarContributionPeriod::stopsContributions)
        .map(SecondPillarContributionPeriod::startDate);
  }

  private static Optional<LocalDate> dayAfterContributionsEndedBefore(
      LocalDate day, List<SecondPillarContributionPeriod> periods) {
    return periods.stream()
        .flatMap(period -> period.lastContributionDayBefore(day).stream())
        .max(naturalOrder())
        .map(lastContributionDay -> lastContributionDay.plusDays(1));
  }
}
