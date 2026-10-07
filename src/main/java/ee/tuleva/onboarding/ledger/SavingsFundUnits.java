package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS_RESERVED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsFundUnits {

  private final LedgerService ledgerService;

  public BigDecimal unitsOutstanding() {
    return unitsOutstandingAccount().getBalance();
  }

  public BigDecimal unitsOutstandingAt(Instant cutoff) {
    return unitsOutstandingAccount().getBalanceAt(cutoff);
  }

  public Optional<BigDecimal> unitsHeldAt(String registryCode, Instant cutoff) {
    var unitAccounts =
        Stream.of(FUND_UNITS, FUND_UNITS_RESERVED)
            .flatMap(
                account ->
                    ledgerService.findPartyAccount(registryCode, LEGAL_ENTITY, account).stream())
            .toList();
    if (unitAccounts.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        unitAccounts.stream()
            .map(account -> account.getBalanceAt(cutoff))
            .reduce(ZERO, BigDecimal::add)
            .negate());
  }

  public int unitHolderCount() {
    return ledgerService.countAccountsWithPositiveBalance(FUND_UNITS);
  }

  private LedgerAccount unitsOutstandingAccount() {
    return ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100);
  }
}
