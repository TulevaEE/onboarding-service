package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.LIABILITY_RECOGNITION;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class LiabilityRecognitionChecker {

  private static final List<String> FEE_ACCRUALS_BOOKED_FROM_OUR_OWN_FEE_LEDGER =
      List.of("Management Fee Payable", "Payables to Depository Bank");

  List<HealthCheckFinding> check(
      TulevaFund fund, LocalDate navDate, List<FundPosition> liabilities) {
    return liabilities.stream()
        .filter(position -> !isRecognised(fund, position))
        .map(position -> unrecognised(fund, navDate, position))
        .toList();
  }

  private boolean isRecognised(TulevaFund fund, FundPosition position) {
    return position.isTradePayable()
        || position.isRedemptionPayableOf(fund)
        || isFeeAccrual(position.getAccountName());
  }

  private boolean isFeeAccrual(String accountName) {
    return FEE_ACCRUALS_BOOKED_FROM_OUR_OWN_FEE_LEDGER.stream().anyMatch(accountName::contains);
  }

  private HealthCheckFinding unrecognised(
      TulevaFund fund, LocalDate navDate, FundPosition position) {
    return new HealthCheckFinding(
        fund,
        LIABILITY_RECOGNITION,
        WARNING,
        ("Unrecognised LIABILITY row stays out of trade payables:"
                + " navDate=%s, accountName=%s, marketValue=%s")
            .formatted(navDate, position.getAccountName(), position.getMarketValue()));
  }
}
