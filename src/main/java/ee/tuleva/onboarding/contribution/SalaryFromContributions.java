package ee.tuleva.onboarding.contribution;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.groupingBy;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.SecondPillarContribution;
import ee.tuleva.onboarding.nudge.MonthlySalary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalaryFromContributions implements MonthlySalary {

  private static final int OLDEST_USABLE_MONTHS_BACK = 2;
  private static final BigDecimal EMPLOYER_RATE = new BigDecimal("4");
  private static final BigDecimal PERCENT = new BigDecimal("100");
  private static final int SALARY_SCALE = 2;

  private final EpisService episService;
  private final Clock estonianClock;

  @Override
  public Optional<BigDecimal> latestGross(Person person, int secondPillarPaymentRate) {
    ZoneId receiptZone = estonianClock.getZone();
    YearMonth thisMonth = YearMonth.now(estonianClock);
    Map<YearMonth, List<SecondPillarContribution>> byReceiptMonth =
        episService.getContributions(person).stream()
            .filter(SecondPillarContribution.class::isInstance)
            .map(SecondPillarContribution.class::cast)
            .filter(contribution -> contribution.amount().signum() > 0)
            .collect(
                groupingBy(
                    contribution -> YearMonth.from(contribution.time().atZone(receiptZone))));
    return byReceiptMonth.entrySet().stream()
        .max(Map.Entry.comparingByKey())
        .filter(month -> isUsable(month.getKey(), thisMonth))
        .map(month -> gross(month.getValue(), secondPillarPaymentRate));
  }

  private static boolean isUsable(YearMonth receiptMonth, YearMonth thisMonth) {
    return !receiptMonth.isAfter(thisMonth)
        && !receiptMonth.isBefore(thisMonth.minusMonths(OLDEST_USABLE_MONTHS_BACK));
  }

  private static BigDecimal gross(List<SecondPillarContribution> month, int paymentRate) {
    BigDecimal received =
        month.stream().map(SecondPillarContribution::amount).reduce(ZERO, BigDecimal::add);
    BigDecimal fromParentalBenefit =
        month.stream()
            .map(SecondPillarContribution::additionalParentalBenefit)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);
    return received
        .subtract(fromParentalBenefit)
        .multiply(PERCENT)
        .divide(EMPLOYER_RATE.add(BigDecimal.valueOf(paymentRate)), SALARY_SCALE, HALF_UP);
  }
}
