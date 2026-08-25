package ee.tuleva.onboarding.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CountriesTest {

  @Test
  void buildsACountryPerCode() {
    assertThat(Countries.of("EE", "RU"))
        .containsExactlyInAnyOrder(new Country("EE"), new Country("RU"));
  }

  @Test
  void dropsMissingAndBlankCodesInsteadOfBuildingEmptyCountries() {
    assertThat(Countries.of(Arrays.asList("EE", null, "", "  ")))
        .containsExactly(new Country("EE"));
  }

  @Test
  void isEmptyWhenNothingIsKnown() {
    assertThat(Countries.of()).isEmpty();
    assertThat(Countries.of(List.of())).isEmpty();
  }

  @Test
  void dedupesRepeatedCodes() {
    assertThat(Countries.of("EE", "EE")).containsExactly(new Country("EE"));
  }
}
