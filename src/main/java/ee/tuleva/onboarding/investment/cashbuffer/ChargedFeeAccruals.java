package ee.tuleva.onboarding.investment.cashbuffer;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ChargedFeeAccruals {

  private final FeeAccrualRepository feeAccrualRepository;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;

  Optional<BigDecimal> accruedDuring(TulevaFund fund, YearMonth month) {
    var feeMonth = month.atDay(1);
    if (!feeAccrualRepository.existsByFundAndFeeMonth(fund, feeMonth)) {
      return Optional.empty();
    }
    return Optional.of(
        Arrays.stream(FeeType.values())
            .map(
                feeType ->
                    feeChargedToFundPolicy
                        .resolverFor(fund, feeType)
                        .sumChargedDays(
                            feeAccrualRepository.getAccruedFeesByDateForMonth(
                                fund, feeMonth, List.of(feeType), month.plusMonths(1).atDay(1))))
            .reduce(ZERO, BigDecimal::add)
            .setScale(2, HALF_UP));
  }
}
