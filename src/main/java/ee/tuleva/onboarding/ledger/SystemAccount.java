package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.SystemAccount.Category.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.Fund.TKF100;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SystemAccount {
  // Payment processing
  INCOMING_PAYMENTS_CLEARING(
      TKF100, ASSET, EUR, PAYMENT_PROCESSING, "Incoming payments before user attribution"),
  PAYOUTS_CASH_CLEARING(
      TKF100, ASSET, EUR, PAYMENT_PROCESSING, "Cash reserved for redemption payouts"),
  BANK_FEE_ACCRUAL(
      TKF100,
      LIABILITY,
      EUR,
      PAYMENT_PROCESSING,
      "Accrued bank transaction fees"), // TODO what is this?

  // Bank reconciliation
  UNRECONCILED_BANK_RECEIPTS(
      TKF100, ASSET, EUR, BANK_RECONCILIATION, "Bank receipts not yet matched"),

  // Fund operations
  FUND_INVESTMENT_CASH_CLEARING(
      TKF100, ASSET, EUR, FUND_OPERATIONS, "Cash reserved for unit purchases"),
  FUND_UNITS_OUTSTANDING(
      TKF100, LIABILITY, FUND_UNIT, FUND_OPERATIONS, "Total units issued to investors"),

  // NAV calculation - Position data (from SEB)
  SECURITIES_VALUE(TKF100, ASSET, EUR, NAV_CALCULATION, "Securities market value from custodian"),
  CASH_POSITION(TKF100, ASSET, EUR, NAV_CALCULATION, "Cash balance from custodian"),
  TRADE_RECEIVABLES(TKF100, ASSET, EUR, NAV_CALCULATION, "Trade settlement receivables"),
  TRADE_PAYABLES(TKF100, LIABILITY, EUR, NAV_CALCULATION, "Trade settlement payables"),

  // NAV calculation - Fee accruals
  MANAGEMENT_FEE_ACCRUAL(TKF100, LIABILITY, EUR, NAV_CALCULATION, "Accrued management fees"),
  DEPOT_FEE_ACCRUAL(TKF100, LIABILITY, EUR, NAV_CALCULATION, "Accrued depot fees"),

  // NAV calculation - Equity (balancing account for positions)
  NAV_EQUITY(TKF100, LIABILITY, EUR, NAV_CALCULATION, "Equity account balancing NAV positions"),

  // NAV calculation - Manual adjustments
  BLACKROCK_ADJUSTMENT(TKF100, ASSET, EUR, NAV_CALCULATION, "Partner rebates/fees adjustment"),

  // Bank operations
  BANK_FEE(TKF100, EXPENSE, EUR, BANK_RECONCILIATION, "Bank transaction fees"),
  INTEREST_INCOME(TKF100, INCOME, EUR, BANK_RECONCILIATION, "Interest income from bank accounts"),
  BANK_ADJUSTMENT(TKF100, EXPENSE, EUR, BANK_RECONCILIATION, "Bank adjustments"),

  TRADE_CASH_SETTLEMENT(TKF100, ASSET, EUR, BANK_RECONCILIATION, "Trade cash settlements"),
  TRADE_UNIT_SETTLEMENT(TKF100, ASSET, FUND_UNIT, BANK_RECONCILIATION, "Trade unit settlements"),
  SECURITIES_CUSTODY(
      TKF100,
      LIABILITY,
      FUND_UNIT,
      BANK_RECONCILIATION,
      "Equity account for balancing unit settlements");

  private final Fund fund;
  private final AccountType accountType;
  private final AssetType assetType;
  private final Category category;
  private final String description;

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

  public enum Category {
    PAYMENT_PROCESSING,
    BANK_RECONCILIATION,
    FUND_OPERATIONS,
    NAV_CALCULATION
  }
}
