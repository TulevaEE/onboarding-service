package ee.tuleva.onboarding.savings.fund.redemption.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaskedIbanTest {

  @Test
  void showsOnlyTheCountryCodeCheckDigitsAndLastFourCharactersInGroupsOfFour() {
    assertThat(MaskedIban.of("EE471000001020145685")).isEqualTo("EE47 **** **** **** 5685");
  }

  @Test
  void keepsThePrintedGroupsOfFourWhenTheLengthIsNotAMultipleOfFour() {
    assertThat(MaskedIban.of("DE00123456789012345678")).isEqualTo("DE00 **** **** **** **56 78");
  }
}
