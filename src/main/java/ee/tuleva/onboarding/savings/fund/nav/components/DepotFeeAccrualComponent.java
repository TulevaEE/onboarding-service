package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.LIABILITY;

import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DepotFeeAccrualComponent implements NavComponent {

  private final FeeAccrualRepository feeAccrualRepository;

  @Override
  public String getName() {
    return "depot_fee_accrual";
  }

  @Override
  public NavComponentType getType() {
    return LIABILITY;
  }

  @Override
  public BigDecimal calculate(NavComponentContext context) {
    BigDecimal accrued =
        feeAccrualRepository.getAccruedFeeAsOf(
            context.getFund(), DEPOT, context.getPositionReportDate());
    if (accrued.signum() < 0) {
      throw new IllegalStateException("Depot fee accrual should be positive, but was: " + accrued);
    }
    return accrued;
  }
}
