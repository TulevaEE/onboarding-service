package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.epis.ContactDetails.LanguagePreferenceType.ENG;
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.mandate.MandateContactDetails;
import ee.tuleva.onboarding.mandate.MandateContactDetails.LanguagePreference;
import org.junit.jupiter.api.Test;

class ContactDetailsMapperTest {

  @Test
  void mapsAFullyPopulatedContactDetails() {
    ContactDetails contactDetails = contactDetailsFixture();

    MandateContactDetails result = ContactDetailsMapper.toMandateContactDetails(contactDetails);

    MandateContactDetails expected =
        MandateContactDetails.builder()
            .email("tuleva@tuleva.ee")
            .address(Country.builder().countryCode("EE").build())
            .secondPillarActive(true)
            .thirdPillarActive(true)
            .noticeNeeded("Y")
            .languagePreference(LanguagePreference.EST)
            .build();
    assertThat(result).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void mapsAContactDetailsWithNoPhoneNoEmailAndNoAddress() {
    ContactDetails contactDetails =
        ContactDetails.builder()
            .firstName("Erko")
            .lastName("Risthein")
            .personalCode("38888888888")
            .country(null)
            .languagePreference(ENG)
            .noticeNeeded("N")
            .email(null)
            .phoneNumber(null)
            .isSecondPillarActive(false)
            .isThirdPillarActive(false)
            .build();

    MandateContactDetails result = ContactDetailsMapper.toMandateContactDetails(contactDetails);

    MandateContactDetails expected =
        MandateContactDetails.builder()
            .email(null)
            .address(Country.builder().countryCode(null).build())
            .secondPillarActive(false)
            .thirdPillarActive(false)
            .noticeNeeded("N")
            .languagePreference(LanguagePreference.ENG)
            .build();
    assertThat(result).usingRecursiveComparison().isEqualTo(expected);
  }
}
