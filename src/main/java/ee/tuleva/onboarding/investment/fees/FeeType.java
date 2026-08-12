package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.ledger.SystemAccount;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeeType {
  MANAGEMENT(SystemAccount.MANAGEMENT_FEE_ACCRUAL),
  DEPOT(SystemAccount.DEPOT_FEE_ACCRUAL);

  private final SystemAccount accrualAccount;
}
