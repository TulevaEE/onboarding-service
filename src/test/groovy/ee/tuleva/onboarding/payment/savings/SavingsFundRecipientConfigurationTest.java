package ee.tuleva.onboarding.payment.savings;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SavingsFundRecipientConfigurationTest {

  @Test
  void validatePassesWithFullyPopulatedConfig() {
    var config = config("Tuleva Täiendav Kogumisfond", "EE711010220306707220");

    assertThatCode(config::validate).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void validateRejectsBlankRecipientName(String value) {
    var config = config(value, "EE711010220306707220");

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("recipient-name");
  }

  @Test
  void validateRejectsNullRecipientName() {
    var config = config(null, "EE711010220306707220");

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("recipient-name");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void validateRejectsBlankRecipientIban(String value) {
    var config = config("Tuleva Täiendav Kogumisfond", value);

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("recipient-iban");
  }

  @Test
  void validateRejectsNullRecipientIban() {
    var config = config("Tuleva Täiendav Kogumisfond", null);

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("recipient-iban");
  }

  private static SavingsFundRecipientConfiguration config(String name, String iban) {
    var config = new SavingsFundRecipientConfiguration();
    config.setRecipientName(name);
    config.setRecipientIban(iban);
    return config;
  }
}
