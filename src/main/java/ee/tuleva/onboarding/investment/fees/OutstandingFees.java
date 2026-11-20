package ee.tuleva.onboarding.investment.fees;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutstandingFees {

  private final FeeAccrualRepository feeAccrualRepository;

  public BigDecimal owedOn(FeeChargedToFundPolicy.Resolver fundFeePolicy, LocalDate date) {
    return roundedToCents(
        fundFeePolicy.sumChargedDays(
            feeAccrualRepository.getUnsettledAccrualByDate(
                fundFeePolicy.fund(), fundFeePolicy.feeType(), date)));
  }

  private static BigDecimal roundedToCents(BigDecimal amount) {
    return amount.signum() == 0 ? BigDecimal.ZERO : amount.setScale(2, HALF_UP);
  }
}
