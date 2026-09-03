package ee.tuleva.onboarding.personalcode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PersonalCodeValidatorTest {

  private final PersonalCodeValidator validator = new PersonalCodeValidator();

  @ParameterizedTest
  @ValueSource(
      strings = {"39001010000", "37508295796", "60001019906", "66003229972", "66112229833"})
  void acceptsChecksumValidCodesIncludingSecondRoundChecksums(String personalCode) {
    assertThat(validator.isValid(personalCode)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "38888888888",
        "39001010001",
        "29001010000",
        "79001010000",
        "39013010009",
        "3900101000",
        "390010100000",
        "3900101000A"
      })
  void rejectsInvalidCodes(String personalCode) {
    assertThat(validator.isValid(personalCode)).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void rejectsMissingCodes(String personalCode) {
    assertThat(validator.isValid(personalCode)).isFalse();
  }
}
