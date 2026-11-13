package ee.tuleva.onboarding.country;

import static ee.tuleva.onboarding.country.EuropeanEconomicArea.COUNTRY_CODES;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EuropeanEconomicAreaTest {

  @Test
  void isTheEuMemberStatesTogetherWithNorwayIcelandAndLiechtenstein() {
    assertThat(COUNTRY_CODES)
        .containsExactlyInAnyOrder(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE",
            "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "NO",
            "IS", "LI");
  }

  @ParameterizedTest
  @ValueSource(strings = {"EE", "ee", "NO", "no", "IS", "is", "LI", "li"})
  void includesAMemberWhateverTheCaseOfItsCode(String countryCode) {
    assertThat(EuropeanEconomicArea.includes(countryCode)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CH", "GB", "RU", "ch", ""})
  void excludesACountryOutsideIt(String countryCode) {
    assertThat(EuropeanEconomicArea.includes(countryCode)).isFalse();
  }
}
