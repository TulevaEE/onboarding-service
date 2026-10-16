package ee.tuleva.onboarding.nudge;

import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.conversion.ConversionHolding;
import ee.tuleva.onboarding.conversion.ConversionHoldings;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FeeComparisonCalculator {

  private final ConversionHoldings conversionHoldings;
  private final FundRepository fundRepository;
  private final Clock estonianClock;

  Optional<FeeComparison> forSecondPillar(Person person, @Nullable BigDecimal weightedFee) {
    if (weightedFee == null || !FundFees.isHigh(weightedFee)) {
      return Optional.empty();
    }
    BigDecimal value =
        conversionHoldings.forPerson(person).stream()
            .filter(holding -> holding.pillar() == 2 && holding.hasAnyValue())
            .map(ConversionHolding::value)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    Fund tulevaFund = fundRepository.findByIsin(TulevaFund.TUK75.getIsin());
    if (value.signum() <= 0 || tulevaFund == null) {
      return Optional.empty();
    }
    BigDecimal tulevaFee = tulevaFund.getOngoingChargesFigure();
    long currentFeeAmount = euros(weightedFee.multiply(value));
    long tulevaFeeAmount = euros(tulevaFee.multiply(value));
    if (currentFeeAmount <= tulevaFeeAmount) {
      return Optional.empty();
    }
    return Optional.of(
        new FeeComparison(
            percent(weightedFee),
            currentFeeAmount,
            tulevaFeeAmount,
            currentFeeAmount - tulevaFeeAmount,
            percent(tulevaFee),
            euros(value),
            LocalDate.now(estonianClock)));
  }

  private static BigDecimal percent(BigDecimal rate) {
    return rate.movePointRight(2).setScale(2, HALF_UP);
  }

  private static long euros(BigDecimal amount) {
    return amount.setScale(0, HALF_UP).longValueExact();
  }
}
