package ee.tuleva.onboarding.country


import static ee.tuleva.onboarding.country.Country.CountryBuilder
import static ee.tuleva.onboarding.country.Country.builder

class CountryFixture {

  static CountryBuilder countryFixture() {
    return builder()
        .countryCode("US")
  }

  static aCountry = countryFixture().build()

}
