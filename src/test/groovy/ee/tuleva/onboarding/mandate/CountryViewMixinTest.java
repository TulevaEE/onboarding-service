package ee.tuleva.onboarding.mandate;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.country.Country;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

@JsonTest
class CountryViewMixinTest {

  @Autowired private JsonMapper jsonMapper;

  @Test
  void serializesCountryFieldsUnderTheMandateDefaultView() {
    var country = new Country("EE");

    String json = jsonMapper.writerWithView(MandateView.Default.class).writeValueAsString(country);

    assertThat(json).contains("\"countryCode\":\"EE\"");
  }
}
