package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.ledger.SystemAccount.DEPOT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.LIABILITY;

import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DepotFeeAccrualComponent implements NavComponent {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final NavLedgerRepository navLedgerRepository;

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
    Instant feeCutoff =
        context
            .getPositionReportDate()
            .plusDays(1)
            .atStartOfDay()
            .atZone(ESTONIAN_ZONE)
            .toInstant();
    BigDecimal balance =
        navLedgerRepository.getSystemAccountBalanceBefore(
            DEPOT_FEE_ACCRUAL.getAccountName(), feeCutoff);
    if (balance.signum() > 0) {
      throw new IllegalStateException(
          "Depot fee accrual balance should be non-positive, but was: " + balance);
    }
    return balance.negate();
  }
}
