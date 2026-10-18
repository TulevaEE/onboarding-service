package ee.tuleva.onboarding.insurance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ErgoPolicyNumberTest {

  @ParameterizedTest
  @ValueSource(strings = {"70-512-345678", "70512345678", "12-345678", "12 345678", "12345678"})
  void splitsThePolicyNumberIntoTheTwoBoxesAfterThePrintedPrefix(String policyNumber) {
    assertThat(ErgoPolicyNumber.parse(policyNumber)).contains(new ErgoPolicyNumber("12", "345678"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234567", "123456789", "71-512-345678", "UP000123"})
  void refusesANumberThatDoesNotFitTheBoxes(String policyNumber) {
    assertThat(ErgoPolicyNumber.parse(policyNumber)).isEqualTo(Optional.empty());
  }
}
