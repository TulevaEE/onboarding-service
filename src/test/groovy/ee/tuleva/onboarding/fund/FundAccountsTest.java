package ee.tuleva.onboarding.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FundAccountsTest {

  private static final String VALID_TEST_IBAN = "EE651010220306497226";

  @Test
  void validate_passesWithCompleteConfiguration() {
    var fundAccounts = fundAccounts(complete());

    fundAccounts.validate();

    assertThat(fundAccounts.cashAccount(TulevaFund.TUK75)).isEqualTo(VALID_TEST_IBAN);
    assertThat(fundAccounts.securitiesAccount(TulevaFund.TUK75)).isEqualTo("VP00001");
    assertThat(fundAccounts.gatewayClientId(TulevaFund.TUK75)).isEqualTo("gw-1");
  }

  @Test
  void validate_failsWhenAFundIsMissing() {
    var accounts = complete();
    accounts.remove(TulevaFund.TUK00);

    assertThatThrownBy(() -> fundAccounts(accounts).validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TUK00");
  }

  @Test
  void validate_failsWhenCashAccountFailsTheIbanChecksum() {
    var accounts = complete();
    accounts.put(TulevaFund.TUK75, new FundAccounts.Account("EE001234567890123456", "VP1", "gw"));

    assertThatThrownBy(() -> fundAccounts(accounts).validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TUK75");
  }

  @Test
  void validate_failsWhenSecuritiesAccountIsBlank() {
    var accounts = complete();
    accounts.put(TulevaFund.TUK00, new FundAccounts.Account(VALID_TEST_IBAN, " ", "gw"));

    assertThatThrownBy(() -> fundAccounts(accounts).validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TUK00");
  }

  @Test
  void validate_failsWhenGatewayClientIdIsBlank() {
    var accounts = complete();
    accounts.put(TulevaFund.TUV100, new FundAccounts.Account(VALID_TEST_IBAN, "VP1", ""));

    assertThatThrownBy(() -> fundAccounts(accounts).validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TUV100");
  }

  private static Map<TulevaFund, FundAccounts.Account> complete() {
    var accounts = new EnumMap<TulevaFund, FundAccounts.Account>(TulevaFund.class);
    accounts.put(TulevaFund.TUK75, new FundAccounts.Account(VALID_TEST_IBAN, "VP00001", "gw-1"));
    accounts.put(TulevaFund.TUK00, new FundAccounts.Account(VALID_TEST_IBAN, "VP00002", "gw-2"));
    accounts.put(TulevaFund.TUV100, new FundAccounts.Account(VALID_TEST_IBAN, "VP00003", "gw-3"));
    accounts.put(TulevaFund.TKF100, new FundAccounts.Account(VALID_TEST_IBAN, "VP00004", "gw-4"));
    return accounts;
  }

  private static FundAccounts fundAccounts(Map<TulevaFund, FundAccounts.Account> accounts) {
    var fundAccounts = new FundAccounts();
    fundAccounts.setFunds(accounts);
    return fundAccounts;
  }
}
