package ee.tuleva.onboarding.kyb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RegistryCodeValidatorTest {

  private final RegistryCodeValidator validator = new RegistryCodeValidator();

  @Test
  void validEightDigitRegistryCode() {
    assertThat(validator.isValid("12345678")).isTrue();
  }

  @Test
  void validRegistryCodeStartingWithZero() {
    assertThat(validator.isValid("00000001")).isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void blankIsInvalid(String value) {
    assertThat(validator.isValid(value)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234567", "123456789", "1234ABCD", "1234 678", "12345.78"})
  void invalidRegistryCodes(String value) {
    assertThat(validator.isValid(value)).isFalse();
  }
}
