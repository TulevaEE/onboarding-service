package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.*;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.UserAccount.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class LedgerAccountFixture {

  public static LedgerAccount.LedgerAccountBuilder sampleLedgerAccount() {
    return LedgerAccount.builder()
        .name(CASH.name())
        .purpose(USER_ACCOUNT)
        .assetType(EUR)
        .accountType(LIABILITY);
  }

  public static LedgerAccount fundUnitsAccountWithBalance(BigDecimal balance) {
    LedgerAccount account =
        LedgerAccount.builder()
            .name(FUND_UNITS.name())
            .purpose(USER_ACCOUNT)
            .assetType(FUND_UNIT)
            .accountType(LIABILITY)
            .build();

    if (balance.compareTo(BigDecimal.ZERO) != 0) {
      // Create a transaction and add entry
      LedgerTransaction transaction =
          LedgerTransaction.builder()
              .transactionDate(Instant.now())
              .metadata(Map.of("test", "fixture"))
              .build();

      // For LIABILITY accounts, negative amount = positive balance
      transaction.addEntry(account, balance.negate());
    }

    return account;
  }

  public static LedgerAccount fundUnitsReservedAccountWithBalance(BigDecimal balance) {
    LedgerAccount account =
        LedgerAccount.builder()
            .name(FUND_UNITS_RESERVED.name())
            .purpose(USER_ACCOUNT)
            .assetType(FUND_UNIT)
            .accountType(LIABILITY)
            .build();

    if (balance.compareTo(BigDecimal.ZERO) != 0) {
      LedgerTransaction transaction =
          LedgerTransaction.builder()
              .transactionDate(Instant.now())
              .metadata(Map.of("test", "fixture"))
              .build();

      // For LIABILITY accounts, negative amount = positive balance
      transaction.addEntry(account, balance.negate());
    }

    return account;
  }

  public static LedgerAccount cashAccountWithBalance(BigDecimal balance) {
    LedgerAccount account =
        LedgerAccount.builder()
            .name(CASH.name())
            .purpose(USER_ACCOUNT)
            .assetType(EUR)
            .accountType(LIABILITY)
            .build();

    if (balance.compareTo(BigDecimal.ZERO) != 0) {
      LedgerTransaction transaction =
          LedgerTransaction.builder()
              .transactionDate(Instant.now())
              .metadata(Map.of("test", "fixture"))
              .build();

      // For LIABILITY accounts, negative amount = positive balance
      transaction.addEntry(account, balance.negate());
    }

    return account;
  }

  public static LedgerAccount cashReservedAccountWithBalance(BigDecimal balance) {
    LedgerAccount account =
        LedgerAccount.builder()
            .name(CASH_RESERVED.name())
            .purpose(USER_ACCOUNT)
            .assetType(EUR)
            .accountType(LIABILITY)
            .build();

    if (balance.compareTo(BigDecimal.ZERO) != 0) {
      LedgerTransaction transaction =
          LedgerTransaction.builder()
              .transactionDate(Instant.now())
              .metadata(Map.of("test", "fixture"))
              .build();

      // For LIABILITY accounts, negative amount = positive balance
      transaction.addEntry(account, balance.negate());
    }

    return account;
  }

  public static LedgerAccount cashRedemptionAccountWithBalance(BigDecimal balance) {
    LedgerAccount account =
        LedgerAccount.builder()
            .name(CASH_REDEMPTION.name())
            .purpose(USER_ACCOUNT)
            .assetType(EUR)
            .accountType(LIABILITY)
            .build();

    if (balance.compareTo(BigDecimal.ZERO) != 0) {
      LedgerTransaction transaction =
          LedgerTransaction.builder()
              .transactionDate(Instant.now())
              .metadata(Map.of("test", "fixture"))
              .build();

      // For LIABILITY accounts, negative amount = positive balance
      transaction.addEntry(account, balance.negate());
    }

    return account;
  }

  public static LedgerAccount subscriptionsAccountWithBalance(BigDecimal balance) {
    LedgerAccount account =
        LedgerAccount.builder()
            .name(SUBSCRIPTIONS.name())
            .purpose(USER_ACCOUNT)
            .assetType(EUR)
            .accountType(INCOME)
            .build();

    if (balance.compareTo(BigDecimal.ZERO) != 0) {
      LedgerTransaction transaction =
          LedgerTransaction.builder()
              .transactionDate(Instant.now())
              .metadata(Map.of("test", "fixture"))
              .build();

      // For INCOME accounts, negative amount = income received
      transaction.addEntry(account, balance.negate());
    }

    return account;
  }

  public static LedgerAccount redemptionsAccountWithBalance(BigDecimal balance) {
    LedgerAccount account =
        LedgerAccount.builder()
            .name(REDEMPTIONS.name())
            .purpose(USER_ACCOUNT)
            .assetType(EUR)
            .accountType(EXPENSE)
            .build();

    if (balance.compareTo(BigDecimal.ZERO) != 0) {
      LedgerTransaction transaction =
          LedgerTransaction.builder()
              .transactionDate(Instant.now())
              .metadata(Map.of("test", "fixture"))
              .build();

      // For EXPENSE accounts, positive amount = expense paid
      transaction.addEntry(account, balance);
    }

    return account;
  }
}
