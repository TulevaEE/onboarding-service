package ee.tuleva.onboarding.party;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PartyCodeValidatorTest {

  private final PartyCodeValidator validator = new PartyCodeValidator();

  @ParameterizedTest
  @ValueSource(strings = {"37605030299", "38812121215", "60001019906"})
  void validPersonalCodes(String code) {
    assertThat(validator.isValid(code, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"12345678", "90000001", "10000000"})
  void validRegistryCodes(String code) {
    assertThat(validator.isValid(code, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234567", "123456789", "abcdefgh", "1234", "", "00000000000"})
  void invalidCodes(String code) {
    assertThat(validator.isValid(code, null)).isFalse();
  }

  @Test
  void nullCode() {
    assertThat(validator.isValid(null, null)).isFalse();
  }

  @Test
  void blankCode() {
    assertThat(validator.isValid("   ", null)).isFalse();
  }
}
