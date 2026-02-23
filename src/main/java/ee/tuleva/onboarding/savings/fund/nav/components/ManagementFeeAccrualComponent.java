package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.LIABILITY;

import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagementFeeAccrualComponent implements NavComponent {

  private final FeeAccrualRepository feeAccrualRepository;

  @Override
  public String getName() {
    return "management_fee_accrual";
  }

  @Override
  public NavComponentType getType() {
    return LIABILITY;
  }

  @Override
  public BigDecimal calculate(NavComponentContext context) {
    BigDecimal accrued =
        feeAccrualRepository.getAccruedFeeAsOf(
            context.getFund(), MANAGEMENT, context.getPositionReportDate());
    if (accrued.signum() < 0) {
      throw new IllegalStateException(
          "Management fee accrual should be positive, but was: " + accrued);
    }
    return accrued;
  }
}
