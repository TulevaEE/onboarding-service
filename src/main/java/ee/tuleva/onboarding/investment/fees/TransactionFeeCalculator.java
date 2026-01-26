package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.zeroAccrual;
import static ee.tuleva.onboarding.investment.fees.FeeType.TRANSACTION;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionFeeCalculator implements FeeCalculator {

  private final FeeMonthResolver feeMonthResolver;

  @Override
  public FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);
    return zeroAccrual(fund, TRANSACTION, calendarDate, feeMonth);
  }

  @Override
  public FeeType getFeeType() {
    return TRANSACTION;
  }
}
