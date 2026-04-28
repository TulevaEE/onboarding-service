package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;

import ee.tuleva.onboarding.analytics.transaction.fundbalance.FundBalance;
import ee.tuleva.onboarding.analytics.transaction.fundbalance.FundBalanceRepository;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AuthoritativeUnitsSource {

  private final FundBalanceRepository fundBalanceRepository;
  private final NavLedgerRepository navLedgerRepository;

  Optional<BigDecimal> resolve(TulevaFund fund) {
    if (fund.isSavingsFund()) {
      return Optional.of(navLedgerRepository.getSystemAccountBalance(accountNameFor(fund)));
    }
    return fundBalanceRepository
        .findFirstByIsinOrderByRequestDateDesc(fund.getIsin())
        .map(AuthoritativeUnitsSource::totalUnits);
  }

  private static BigDecimal totalUnits(FundBalance balance) {
    return balance.getCountUnits().add(balance.getCountUnitsFm());
  }

  private static String accountNameFor(TulevaFund fund) {
    return FUND_UNITS_OUTSTANDING.getAccountName(fund);
  }
}
