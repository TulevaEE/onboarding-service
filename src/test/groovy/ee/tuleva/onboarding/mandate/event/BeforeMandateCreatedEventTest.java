package ee.tuleva.onboarding.mandate.event;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.country.CountryFixture.countryFixture;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BeforeMandateCreatedEventTest {

  @Test
  @DisplayName("getCountry returns country from mandate")
  void getCountry_returnsCountryFromMandate() {
    Country country = countryFixture().build();
    User user = sampleUser().build();
    Mandate mandate =
        Mandate.builder().address(country).pillar(2).metadata(new java.util.HashMap<>()).build();

    var event = new BeforeMandateCreatedEvent(this, user, mandate);

    assertThat(event.getCountry()).isEqualTo(country);
  }

  @Test
  @DisplayName("getPillar returns pillar from mandate")
  void getPillar_returnsPillarFromMandate() {
    Country country = countryFixture().build();
    User user = sampleUser().build();
    Mandate mandate =
        Mandate.builder().address(country).pillar(3).metadata(new java.util.HashMap<>()).build();

    var event = new BeforeMandateCreatedEvent(this, user, mandate);

    assertThat(event.getPillar()).isEqualTo(3);
  }

  @Test
  @DisplayName("isThirdPillar returns true for third pillar mandate")
  void isThirdPillar_returnsTrueForThirdPillar() {
    Country country = countryFixture().build();
    User user = sampleUser().build();
    Mandate mandate =
        Mandate.builder().address(country).pillar(3).metadata(new java.util.HashMap<>()).build();

    var event = new BeforeMandateCreatedEvent(this, user, mandate);

    assertThat(event.isThirdPillar()).isTrue();
  }

  @Test
  @DisplayName("isThirdPillar returns false for second pillar mandate")
  void isThirdPillar_returnsFalseForSecondPillar() {
    Country country = countryFixture().build();
    User user = sampleUser().build();
    Mandate mandate =
        Mandate.builder().address(country).pillar(2).metadata(new java.util.HashMap<>()).build();

    var event = new BeforeMandateCreatedEvent(this, user, mandate);

    assertThat(event.isThirdPillar()).isFalse();
  }
}
