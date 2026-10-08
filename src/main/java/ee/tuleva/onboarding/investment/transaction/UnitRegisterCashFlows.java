package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.UNRECONCILED_BANK_RECEIPTS;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS_RESERVED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class UnitRegisterCashFlows {

  private final NavLedgerRepository navLedgerRepository;
  private final FundValueQueries fundValueQueries;

  UnitRegisterCash read(TulevaFund fund) {
    if (fund != TKF100) {
      return UnitRegisterCash.NONE;
    }
    return new UnitRegisterCash(
        navLedgerRepository.getSystemAccountBalance(
            INCOMING_PAYMENTS_CLEARING.getAccountName(fund)),
        unattributedPaymentsHeld(fund),
        unitsReservedForRedemptionValue(fund));
  }

  private BigDecimal unattributedPaymentsHeld(TulevaFund fund) {
    return navLedgerRepository
        .getSystemAccountBalance(UNRECONCILED_BANK_RECEIPTS.getAccountName(fund))
        .negate();
  }

  private BigDecimal unitsReservedForRedemptionValue(TulevaFund fund) {
    BigDecimal reservedUnits =
        navLedgerRepository.getFundUnitsBalance(FUND_UNITS_RESERVED.name()).negate();
    if (reservedUnits.signum() == 0) {
      return ZERO;
    }
    return reservedUnits.multiply(latestNav(fund, reservedUnits));
  }

  private BigDecimal latestNav(TulevaFund fund, BigDecimal reservedUnits) {
    return fundValueQueries
        .findLastValueForFund(fund.getIsin())
        .map(FundValue::value)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No NAV to value the units reserved for redemption: fund="
                        + fund
                        + ", reservedUnits="
                        + reservedUnits));
  }
}
