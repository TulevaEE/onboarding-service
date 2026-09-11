package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankAccountType.*;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.fund.FundAccounts;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SebBankAccountsTest {

  private static final String DEPOSIT_IBAN = "EE111111111111111111";
  private static final String WITHDRAWAL_IBAN = "EE222222222222222222";
  private static final String FUND_INVESTMENT_IBAN = "EE333333333333333333";
  private static final String TUK75_IBAN = "EE001234567890123475";
  private static final String TUK00_IBAN = "EE001234567890123400";
  private static final String TUV100_IBAN = "EE001234567890123410";

  private final SebBankAccounts bankAccounts =
      new SebBankAccounts(
          new SebAccountConfiguration(
              Map.of(
                  DEPOSIT_EUR, DEPOSIT_IBAN,
                  WITHDRAWAL_EUR, WITHDRAWAL_IBAN,
                  FUND_INVESTMENT_EUR, FUND_INVESTMENT_IBAN),
              "Tuleva Fondid AS",
              List.of(),
              null,
              null),
          testFundAccounts());

  private static FundAccounts testFundAccounts() {
    var accounts = new EnumMap<TulevaFund, FundAccounts.Account>(TulevaFund.class);
    accounts.put(TKF100, new FundAccounts.Account("EE001234567890123458", "VP0", "gw-tkf100"));
    accounts.put(TUK75, new FundAccounts.Account(TUK75_IBAN, "VP1", "gw-tuk75"));
    accounts.put(TUK00, new FundAccounts.Account(TUK00_IBAN, "VP2", "gw-tuk00"));
    accounts.put(TUV100, new FundAccounts.Account(TUV100_IBAN, "VP3", "gw-tuv100"));
    var fundAccounts = new FundAccounts();
    fundAccounts.setFunds(accounts);
    return fundAccounts;
  }

  @Test
  void find_returnsSavingsFundAccountForConfiguredIban() {
    assertThat(bankAccounts.find(DEPOSIT_IBAN))
        .contains(new BankAccount(DEPOSIT_IBAN, DEPOSIT_EUR, TKF100, "gw-tkf100"));
    assertThat(bankAccounts.find(WITHDRAWAL_IBAN))
        .contains(new BankAccount(WITHDRAWAL_IBAN, WITHDRAWAL_EUR, TKF100, "gw-tkf100"));
    assertThat(bankAccounts.find(FUND_INVESTMENT_IBAN))
        .contains(new BankAccount(FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR, TKF100, "gw-tkf100"));
  }

  @Test
  void find_isEmptyForUnknownIban() {
    assertThat(bankAccounts.find("EE999999999999999999")).isEmpty();
  }

  @Test
  void getIban_returnsIbanForFundAndType() {
    assertThat(bankAccounts.getIban(TKF100, DEPOSIT_EUR)).isEqualTo(DEPOSIT_IBAN);
    assertThat(bankAccounts.getIban(TKF100, WITHDRAWAL_EUR)).isEqualTo(WITHDRAWAL_IBAN);
    assertThat(bankAccounts.getIban(TKF100, FUND_INVESTMENT_EUR)).isEqualTo(FUND_INVESTMENT_IBAN);
    assertThat(bankAccounts.getIban(TUK75, FUND_INVESTMENT_EUR)).isEqualTo(TUK75_IBAN);
  }

  @Test
  void getIban_throwsForFundAndTypeCombinationWithoutAnAccount() {
    assertThatThrownBy(() -> bankAccounts.getIban(TUK75, DEPOSIT_EUR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TUK75");
  }

  @Test
  void construction_failsWhenAccountTypeIsMissing() {
    var configuration =
        new SebAccountConfiguration(
            Map.of(DEPOSIT_EUR, DEPOSIT_IBAN), "Tuleva Fondid AS", List.of(), null, null);

    assertThatThrownBy(() -> new SebBankAccounts(configuration, testFundAccounts()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("WITHDRAWAL_EUR")
        .hasMessageContaining("FUND_INVESTMENT_EUR");
  }

  @Test
  void construction_failsWhenIbanIsBlank() {
    var configuration =
        new SebAccountConfiguration(
            Map.of(
                DEPOSIT_EUR, DEPOSIT_IBAN,
                WITHDRAWAL_EUR, "",
                FUND_INVESTMENT_EUR, FUND_INVESTMENT_IBAN),
            "Tuleva Fondid AS",
            List.of(),
            null,
            null);

    assertThatThrownBy(() -> new SebBankAccounts(configuration, testFundAccounts()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("WITHDRAWAL_EUR");
  }

  @Test
  void pensionFundCashAccountsAreRegisteredFromFundAccountsConfiguration() {
    assertThat(bankAccounts.find(TUK75_IBAN))
        .contains(new BankAccount(TUK75_IBAN, FUND_INVESTMENT_EUR, TUK75, "gw-tuk75"));
    assertThat(bankAccounts.find(TUK00_IBAN))
        .contains(new BankAccount(TUK00_IBAN, FUND_INVESTMENT_EUR, TUK00, "gw-tuk00"));
    assertThat(bankAccounts.find(TUV100_IBAN))
        .contains(new BankAccount(TUV100_IBAN, FUND_INVESTMENT_EUR, TUV100, "gw-tuv100"));
  }

  @Test
  void findAll_returnsSavingsFundAccountsFirstThenPensionFunds() {
    assertThat(bankAccounts.findAll())
        .extracting(BankAccount::fund)
        .containsExactly(TKF100, TKF100, TKF100, TUK75, TUK00, TUV100);
  }

  @Test
  void findAll_byFundReturnsOnlyThatFundsAccounts() {
    assertThat(bankAccounts.findAll(TKF100)).hasSize(3);
  }

  @Test
  void savingsFundAccountsComeOnlyFromSebGatewayConfiguration() {
    assertThat(bankAccounts.findAll(TKF100))
        .extracting(BankAccount::iban)
        .containsExactly(DEPOSIT_IBAN, WITHDRAWAL_IBAN, FUND_INVESTMENT_IBAN);
  }

  @Test
  void duplicateIban_failsAtConstruction() {
    var configuration =
        new SebAccountConfiguration(
            Map.of(
                DEPOSIT_EUR, DEPOSIT_IBAN,
                WITHDRAWAL_EUR, DEPOSIT_IBAN,
                FUND_INVESTMENT_EUR, FUND_INVESTMENT_IBAN),
            "Tuleva Fondid AS",
            List.of(),
            null,
            null);

    assertThatThrownBy(() -> new SebBankAccounts(configuration, testFundAccounts()))
        .isInstanceOf(IllegalStateException.class);
  }
}
