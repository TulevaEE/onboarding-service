package ee.tuleva.onboarding.epis;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.country.Country;
import org.junit.jupiter.api.Test;

class ContactDetailsTest {

  @Test
  void setAddress_returnsThisForChainingAndUpdatesCountry() {
    ContactDetails contactDetails = ContactDetails.builder().build();
    Country address = Country.builder().countryCode("EE").build();

    ContactDetails result = contactDetails.setAddress(address);

    assertThat(result).isSameAs(contactDetails);
    assertThat(contactDetails.getAddress()).isEqualTo(address);
  }
}
