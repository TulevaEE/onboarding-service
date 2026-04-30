package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.COOP_WEB;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.LHV;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.PARTNER;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SEB;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.SWEDBANK;
import static ee.tuleva.onboarding.payment.recurring.ThirdPillarRecipientConfigurationFixture.thirdPillarRecipientConfiguration;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ThirdPillarRecipientConfigurationTest {

  @Test
  void validatePassesWithFullyPopulatedConfig() {
    var config = thirdPillarRecipientConfiguration();

    assertThatCode(config::validate).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void validateRejectsBlankRecipientName(String value) {
    var config = thirdPillarRecipientConfiguration();
    config.setRecipientName(value);

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("recipient-name");
  }

  @Test
  void validateRejectsNullRecipientName() {
    var config = thirdPillarRecipientConfiguration();
    config.setRecipientName(null);

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("recipient-name");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void validateRejectsBlankDescription(String value) {
    var config = thirdPillarRecipientConfiguration();
    config.setDescription(value);

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("description");
  }

  @Test
  void validateRejectsNullDescription() {
    var config = thirdPillarRecipientConfiguration();
    config.setDescription(null);

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("description");
  }

  @ParameterizedTest
  @EnumSource(
      value = PaymentChannel.class,
      names = {"LHV", "SEB", "SWEDBANK", "COOP", "COOP_WEB", "PARTNER"})
  void validateRejectsMissingBankAccountForRequiredChannel(PaymentChannel channel) {
    var config = thirdPillarRecipientConfiguration();
    var accounts = new HashMap<>(config.getBankAccounts());
    accounts.remove(channel);
    config.setBankAccounts(accounts);

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("bank-accounts")
        .hasMessageContaining(channel.name());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void validateRejectsBlankBankAccountForRequiredChannel(String value) {
    var config = thirdPillarRecipientConfiguration();
    var accounts = new HashMap<>(config.getBankAccounts());
    accounts.put(LHV, value);
    config.setBankAccounts(accounts);

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("bank-accounts")
        .hasMessageContaining("LHV");
  }

  @Test
  void validateRejectsNullBankAccountsMap() {
    var config = thirdPillarRecipientConfiguration();
    config.setBankAccounts(null);

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("bank-accounts");
  }

  @Test
  void validatePassesWithExtraOptionalChannels() {
    var config = thirdPillarRecipientConfiguration();
    var accounts = new HashMap<>(config.getBankAccounts());
    accounts.put(SWEDBANK, "EE362200221067235244");
    accounts.put(SEB, "EE141010220263146225");
    accounts.put(COOP, "EE362200221067235244");
    accounts.put(COOP_WEB, "EE362200221067235244");
    accounts.put(PARTNER, "EE362200221067235244");
    config.setBankAccounts(accounts);

    assertThatCode(config::validate).doesNotThrowAnyException();
  }
}
