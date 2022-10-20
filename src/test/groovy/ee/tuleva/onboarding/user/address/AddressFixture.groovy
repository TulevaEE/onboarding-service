package ee.tuleva.onboarding.user.address


import static ee.tuleva.onboarding.user.address.Address.AddressBuilder
import static ee.tuleva.onboarding.user.address.Address.builder

class AddressFixture {

  static AddressBuilder addressFixture() {
    return builder()
        .street("Telliskivi 123")
        .countryCode("US")
        .postalCode("99999")
        .districtCode("0123")
  }

  static anAddress = addressFixture().build()

}
