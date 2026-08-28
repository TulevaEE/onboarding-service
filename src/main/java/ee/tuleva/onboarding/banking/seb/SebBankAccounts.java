package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.fund.FundAccounts;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SebBankAccounts implements BankAccounts {

  private final Map<String, BankAccount> accountsByIban;

  public SebBankAccounts(SebAccountConfiguration configuration, FundAccounts fundAccounts) {
    requireCompleteSavingsFundConfiguration(configuration.getAccounts());
    this.accountsByIban =
        indexByIban(
            Stream.concat(
                savingsFundAccounts(configuration.getAccounts(), fundAccounts),
                pensionFundAccounts(fundAccounts)));
  }

  private static void requireCompleteSavingsFundConfiguration(
      Map<BankAccountType, String> accounts) {
    var missing =
        Arrays.stream(BankAccountType.values())
            .filter(type -> accounts.get(type) == null || accounts.get(type).isBlank())
            .toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Savings fund bank accounts are not fully configured: missing=%s".formatted(missing));
    }
  }

  @Override
  public String getIban(TulevaFund fund, BankAccountType type) {
    return accountsByIban.values().stream()
        .filter(account -> account.matches(fund, type))
        .findFirst()
        .map(BankAccount::iban)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No bank account found: fund=%s, type=%s".formatted(fund, type)));
  }

  @Override
  public Optional<BankAccount> find(String iban) {
    return Optional.ofNullable(accountsByIban.get(iban));
  }

  @Override
  public List<BankAccount> findAll() {
    return List.copyOf(accountsByIban.values());
  }

  @Override
  public List<BankAccount> findAll(TulevaFund fund) {
    return accountsByIban.values().stream().filter(account -> account.belongsTo(fund)).toList();
  }

  private static Stream<BankAccount> savingsFundAccounts(
      Map<BankAccountType, String> accounts, FundAccounts fundAccounts) {
    return Arrays.stream(BankAccountType.values())
        .filter(accounts::containsKey)
        .map(
            type ->
                new BankAccount(
                    requireNonNull(
                        accounts.get(type), "Missing savings fund bank account: type=" + type),
                    type,
                    TKF100,
                    fundAccounts.gatewayClientId(TKF100)));
  }

  private static Stream<BankAccount> pensionFundAccounts(FundAccounts fundAccounts) {
    return Arrays.stream(TulevaFund.values())
        .filter(fund -> !fund.isSavingsFund())
        .map(
            fund ->
                new BankAccount(
                    fundAccounts.cashAccount(fund),
                    FUND_INVESTMENT_EUR,
                    fund,
                    fundAccounts.gatewayClientId(fund)));
  }

  private static Map<String, BankAccount> indexByIban(Stream<BankAccount> accounts) {
    var index = new LinkedHashMap<String, BankAccount>();
    accounts.forEach(
        account -> {
          var previous = index.put(account.iban(), account);
          if (previous != null) {
            throw new IllegalStateException(
                "Duplicate bank account iban: %s and %s".formatted(previous, account));
          }
        });
    return index;
  }
}
