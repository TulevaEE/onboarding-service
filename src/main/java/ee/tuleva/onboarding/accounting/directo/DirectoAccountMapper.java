package ee.tuleva.onboarding.accounting.directo;

import static ee.tuleva.onboarding.accounting.directo.DirectoFields.require;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.EQUITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.EXPENSE;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.OFF_BALANCE;

import ee.tuleva.onboarding.ledger.GeneralLedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class DirectoAccountMapper {

  private DirectoAccountMapper() {}

  static GeneralLedgerAccount toGeneralLedgerAccount(DirectoAccount account) {
    var accountClass = require(account.accountClass(), "accounts.class");
    return new GeneralLedgerAccount(
        require(account.code(), "accounts.code"),
        accountType(accountClass),
        metadata(account, accountClass));
  }

  private static AccountType accountType(String accountClass) {
    final Map<String, AccountType> ACCOUNT_TYPES_BY_CLASS =
        Map.of(
            "0", ASSET, "1", LIABILITY, "2", EQUITY, "3", INCOME, "4", EXPENSE, "5", OFF_BALANCE);
    var accountType = ACCOUNT_TYPES_BY_CLASS.get(accountClass.trim());
    if (accountType == null) {
      throw new IllegalArgumentException("Directo account class unknown: field=accounts.class");
    }
    return accountType;
  }

  private static Map<String, Object> metadata(DirectoAccount account, String accountClass) {
    var datafields = Objects.requireNonNullElse(account.datafields(), List.<DirectoDatafield>of());
    var metadata = new HashMap<String, Object>();
    putIfPresent(metadata, "name", account.name());
    putIfPresent(metadata, "nameEnglish", englishName(datafields));
    putIfPresent(metadata, "class", accountClass);
    putIfPresent(metadata, "correspondenceCode", account.correspondenceCode());
    putIfPresent(metadata, "cashFlowDirect", content(datafields, "RV_OTSE"));
    putIfPresent(metadata, "cashFlowIndirect", content(datafields, "RV_KAUDNE"));
    putIfPresent(metadata, "cashFlowIndirectAdjustment", content(datafields, "RV_KAUDNE_KORR"));
    metadata.put("source", DirectoPartMapper.SOURCE);
    return Map.copyOf(metadata);
  }

  private static @Nullable String englishName(List<DirectoDatafield> datafields) {
    return datafields.stream()
        .filter(datafield -> "LISANIMI".equals(datafield.code()))
        .filter(datafield -> "ENG".equals(datafield.param()))
        .map(DirectoDatafield::content)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static @Nullable String content(List<DirectoDatafield> datafields, String code) {
    return datafields.stream()
        .filter(datafield -> code.equals(datafield.code()))
        .map(DirectoDatafield::content)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static void putIfPresent(
      Map<String, Object> metadata, String key, @Nullable String value) {
    if (value != null && !value.isBlank()) {
      metadata.put(key, value);
    }
  }
}
