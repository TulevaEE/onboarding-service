package ee.tuleva.onboarding.investment.transaction;

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
        navLedgerRepository.getSystemAccountBalance("INCOMING_PAYMENTS_CLEARING"),
        navLedgerRepository.getSystemAccountBalance("UNRECONCILED_BANK_RECEIPTS"),
        fundUnitsReservedValue());
  }

  private BigDecimal fundUnitsReservedValue() {
    BigDecimal units = navLedgerRepository.getFundUnitsBalance("FUND_UNITS_RESERVED");
    if (units.signum() == 0) {
      return ZERO;
    }
    BigDecimal nav =
        fundValueQueries.findLastValueForFund(TKF100.getIsin()).map(FundValue::value).orElse(ZERO);
    return units.multiply(nav);
  }
}
