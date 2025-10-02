package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserAccount {
  CASH(LIABILITY, EUR),
  CASH_RESERVED(LIABILITY, EUR),
  CASH_REDEMPTION(LIABILITY, EUR),
  FUND_UNITS(LIABILITY, FUND_UNIT),
  FUND_UNITS_RESERVED(LIABILITY, FUND_UNIT),
  SUBSCRIPTIONS(INCOME, EUR),
  REDEMPTIONS(EXPENSE, EUR);

  private final AccountType accountType;
  private final AssetType assetType;
}
