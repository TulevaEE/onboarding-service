package ee.tuleva.onboarding.capital.transfer.iban;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IbanValidatorTest {

  private IbanValidator validator;

  @BeforeEach
  void setUp() {
    validator = new IbanValidator();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "DE75512108001245126199", // Germany
        "FR7630006000011234567890189", // France
        "EE471000001020145685", // Estonia
        "PL10105000997603123456789123", // Poland
        "CH5604835012345678009", // Switzerland
        "AE460090000000123456789", // United Arab Emirates
        "BR1500000000000010932840814P2", // Brazil
        "QA54QNBA000000000000693123456", // Qatar
      })
  @DisplayName("valid IBANs (global) should pass")
  void isValid_whenValidIban_thenTrue(String iban) {
    assertThat(validator.isValid(iban, null)).isTrue();
  }

  @Test
  @DisplayName("valid IBAN with spaces should pass")
  void isValid_whenIbanWithSpaces_thenTrue() {
    assertThat(validator.isValid("GB33 BUKB 2020 1555 5555 55", null)).isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        " ", // blank
        "EE1234", // too short
        "EE471000001020145686", // bad checksum
        "US64SVBKUS6S3300958879", // country not using IBAN
        "AT483200000012345864!", // special char
        "FR76300060000112345678901891", // too long
        "NO833000123456", // too short for NO
        "SA4420000001234567891235" // checksum off
      })
  @DisplayName("invalid IBANs should fail")
  void isValid_whenInvalidIban_thenFalse(String iban) {
    assertThat(validator.isValid(iban, null)).isFalse();
  }
}
