package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.SystemAccount.Fund.TKF100;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SystemAccount {
  INCOMING_PAYMENTS_CLEARING(TKF100, ASSET, EUR),
  UNRECONCILED_BANK_RECEIPTS(TKF100, ASSET, EUR),
  FUND_INVESTMENT_CASH_CLEARING(TKF100, ASSET, EUR),
  FUND_UNITS_OUTSTANDING(TKF100, LIABILITY, FUND_UNIT),
  PAYOUTS_CASH_CLEARING(TKF100, ASSET, EUR),
  BANK_FEE(TKF100, EXPENSE, EUR),
  BANK_ADJUSTMENT(TKF100, EXPENSE, EUR),
  INTEREST_INCOME(TKF100, INCOME, EUR),

  TRADE_CASH_SETTLEMENT(TKF100, ASSET, EUR),
  TRADE_UNIT_SETTLEMENT(TKF100, ASSET, FUND_UNIT),
  SECURITIES_CUSTODY(TKF100, LIABILITY, FUND_UNIT);

  private final Fund fund;
  private final AccountType accountType;
  private final AssetType assetType;

  public String getAccountName() {
    return name() + ":" + fund.name();
  }

  public String getAccountName(String instrument) {
    return name() + ":" + fund.name() + ":" + instrument;
  }

  public static SystemAccount fromAccountName(String accountName) {
    return Arrays.stream(values())
        .filter(sa -> accountName.equals(sa.name()) || accountName.startsWith(sa.name() + ":"))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown system account: " + accountName));
  }

  enum Fund {
    TKF100
  }
}
