package ee.tuleva.onboarding.investment.cashbuffer;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.function.Predicate.not;

import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy.Resolver;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ChargedFeeAccruals {

  private final FeeAccrualRepository feeAccrualRepository;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;

  Optional<BigDecimal> accruedDuring(TulevaFund fund, YearMonth month) {
    var accrualsByFeeType =
        Arrays.stream(FeeType.values())
            .map(
                feeType ->
                    new FeeTypeAccruals(
                        feeChargedToFundPolicy.resolverFor(fund, feeType),
                        feeAccrualRepository.getAccruedFeesByDateForMonth(
                            fund, month.atDay(1), List.of(feeType), month.plusMonths(1).atDay(1))))
            .toList();
    if (accrualsByFeeType.stream().anyMatch(accruals -> accruals.missesAChargedDayOf(month))) {
      return Optional.empty();
    }
    return Optional.of(
        accrualsByFeeType.stream()
            .map(FeeTypeAccruals::charged)
            .reduce(ZERO, BigDecimal::add)
            .setScale(2, HALF_UP));
  }

  private record FeeTypeAccruals(Resolver policy, Map<LocalDate, BigDecimal> amountsByDate) {

    boolean missesAChargedDayOf(YearMonth month) {
      return month
          .atDay(1)
          .datesUntil(month.plusMonths(1).atDay(1))
          .filter(policy::chargedOn)
          .anyMatch(not(amountsByDate::containsKey));
    }

    BigDecimal charged() {
      return policy.sumChargedDays(amountsByDate);
    }
  }
}
