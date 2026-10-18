package ee.tuleva.onboarding.epis;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.country.Country;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ContactDetailsTest {

  @Test
  void setAddress_returnsThisForChainingAndUpdatesCountry() {
    ContactDetails contactDetails = ContactDetails.builder().build();
    Country address = Country.builder().countryCode("EE").build();

    ContactDetails result = contactDetails.setAddress(address);

    assertThat(result).isSameAs(contactDetails);
    assertThat(contactDetails.getAddress()).isEqualTo(address);
  }

  @Test
  void hasPensionAccountOnceTheRegistryHasGivenItANumber() {
    ContactDetails contactDetails =
        ContactDetails.builder().pensionAccountNumber("993432432").build();

    assertThat(contactDetails.hasPensionAccount()).isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void hasNoPensionAccountWithoutANumber(String pensionAccountNumber) {
    ContactDetails contactDetails =
        ContactDetails.builder().pensionAccountNumber(pensionAccountNumber).build();

    assertThat(contactDetails.hasPensionAccount()).isFalse();
  }
}
