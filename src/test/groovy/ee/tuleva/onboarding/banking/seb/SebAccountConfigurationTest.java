package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankAccountType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SebAccountConfigurationTest {

  private static final String DEPOSIT_IBAN = "EE111111111111111111";
  private static final String WITHDRAWAL_IBAN = "EE222222222222222222";
  private static final String FUND_INVESTMENT_IBAN = "EE333333333333333333";

  private final SebAccountConfiguration configuration =
      new SebAccountConfiguration(
          Map.of(
              DEPOSIT_EUR, DEPOSIT_IBAN,
              WITHDRAWAL_EUR, WITHDRAWAL_IBAN,
              FUND_INVESTMENT_EUR, FUND_INVESTMENT_IBAN));

  @Test
  void getAccountIban_returnsIbanForAccountType() {
    configuration.mapByIban();

    assertThat(configuration.getAccountIban(DEPOSIT_EUR)).isEqualTo(DEPOSIT_IBAN);
    assertThat(configuration.getAccountIban(WITHDRAWAL_EUR)).isEqualTo(WITHDRAWAL_IBAN);
    assertThat(configuration.getAccountIban(FUND_INVESTMENT_EUR)).isEqualTo(FUND_INVESTMENT_IBAN);
  }

  @Test
  void getAccountIban_throwsWhenAccountNotConfigured() {
    var emptyConfiguration = new SebAccountConfiguration(Map.of());

    assertThatThrownBy(() -> emptyConfiguration.getAccountIban(DEPOSIT_EUR))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getAccountType_returnsAccountTypeForIban() {
    configuration.mapByIban();

    assertThat(configuration.getAccountType(DEPOSIT_IBAN)).isEqualTo(DEPOSIT_EUR);
    assertThat(configuration.getAccountType(WITHDRAWAL_IBAN)).isEqualTo(WITHDRAWAL_EUR);
    assertThat(configuration.getAccountType(FUND_INVESTMENT_IBAN)).isEqualTo(FUND_INVESTMENT_EUR);
  }

  @Test
  void getAccountType_returnsNullForUnknownIban() {
    configuration.mapByIban();

    assertThat(configuration.getAccountType("EE999999999999999999")).isNull();
  }
}
