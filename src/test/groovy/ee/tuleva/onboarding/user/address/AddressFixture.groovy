package ee.tuleva.onboarding.user.address


import static ee.tuleva.onboarding.user.address.Address.AddressBuilder
import static ee.tuleva.onboarding.user.address.Address.builder

class AddressFixture {

  static AddressBuilder addressFixture() {
    return builder()
        .countryCode("US")
  }

  static anAddress = addressFixture().build()

}
