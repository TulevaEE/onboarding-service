package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.country.Country

import static ee.tuleva.onboarding.mandate.MandateContactDetails.LanguagePreference.EST

class MandateContactDetailsFixture {

  static MandateContactDetails contactDetailsFixture() {
    return MandateContactDetails.builder()
        .email("tuleva@tuleva.ee")
        .address(Country.builder().countryCode("EE").build())
        .secondPillarActive(true)
        .thirdPillarActive(true)
        .noticeNeeded("Y")
        .languagePreference(EST)
        .build()
  }
}
