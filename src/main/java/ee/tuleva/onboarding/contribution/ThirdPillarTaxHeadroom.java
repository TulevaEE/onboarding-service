package ee.tuleva.onboarding.contribution;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ThirdPillarTaxHeadroom {

  private static final BigDecimal STATE_SOCIAL_TAX_RATE = new BigDecimal("0.04");
  private static final BigDecimal DEDUCTIBLE_SHARE_OF_GROSS = new BigDecimal("0.15");
  private static final BigDecimal ANNUAL_CEILING = new BigDecimal("6000");
  private static final BigDecimal CONFIDENT_HEADROOM_SHARE = new BigDecimal("0.8");

  private final EpisService episService;
  private final Clock clock;

  public boolean hasHeadroom(Person person) {
    try {
      return hasConfidentHeadroom(person);
    } catch (Exception e) {
      log.warn("Skipping third pillar raise nudge, contribution lookup failed", e);
      return false;
    }
  }

  private boolean hasConfidentHeadroom(Person person) {
    List<Contribution> contributions = episService.getContributions(person);
    BigDecimal monthlyGross = deriveMonthlyGross(contributions);
    if (monthlyGross == null) {
      return false;
    }
    BigDecimal ceiling =
        monthlyGross
            .multiply(BigDecimal.valueOf(12))
            .multiply(DEDUCTIBLE_SHARE_OF_GROSS)
            .min(ANNUAL_CEILING);
    BigDecimal confidentCeiling = ceiling.multiply(CONFIDENT_HEADROOM_SHARE);
    BigDecimal lastYear = thirdPillarContributionsBetween(contributions, yearsAgo(1), now());
    BigDecimal yearBefore =
        thirdPillarContributionsBetween(contributions, yearsAgo(2), yearsAgo(1));
    return lastYear.compareTo(confidentCeiling) < 0 && yearBefore.compareTo(confidentCeiling) < 0;
  }

  private BigDecimal deriveMonthlyGross(List<Contribution> contributions) {
    List<SecondPillarContribution> secondPillar =
        contributions.stream()
            .filter(SecondPillarContribution.class::isInstance)
            .map(SecondPillarContribution.class::cast)
            .filter(contribution -> contribution.socialTaxPortion() != null)
            .filter(contribution -> contribution.socialTaxPortion().signum() > 0)
            .toList();
    if (secondPillar.isEmpty()) {
      return null;
    }
    Instant yearAgo = yearAgo();
    List<SecondPillarContribution> recent =
        secondPillar.stream()
            .filter(contribution -> !contribution.time().isBefore(yearAgo))
            .toList();
    if (recent.isEmpty()) {
      return null;
    }
    List<BigDecimal> monthlyPortions =
        recent.stream()
            .collect(
                Collectors.groupingBy(
                    contribution -> YearMonth.from(contribution.time().atZone(clock.getZone())),
                    Collectors.mapping(
                        SecondPillarContribution::socialTaxPortion,
                        Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))))
            .values()
            .stream()
            .sorted()
            .toList();
    BigDecimal medianPortion = median(monthlyPortions);
    return medianPortion.divide(STATE_SOCIAL_TAX_RATE, 2, RoundingMode.DOWN);
  }

  private static BigDecimal median(List<BigDecimal> sorted) {
    int middle = sorted.size() / 2;
    if (sorted.size() % 2 == 1) {
      return sorted.get(middle);
    }
    return sorted
        .get(middle - 1)
        .add(sorted.get(middle))
        .divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
  }

  private static BigDecimal thirdPillarContributionsBetween(
      List<Contribution> contributions, Instant from, Instant to) {
    return contributions.stream()
        .filter(ThirdPillarContribution.class::isInstance)
        .filter(
            contribution -> !contribution.time().isBefore(from) && contribution.time().isBefore(to))
        .map(Contribution::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Instant now() {
    return clock.instant();
  }

  private Instant yearAgo() {
    return yearsAgo(1);
  }

  private Instant yearsAgo(int years) {
    return ZonedDateTime.now(clock).minusYears(years).toInstant();
  }
}
