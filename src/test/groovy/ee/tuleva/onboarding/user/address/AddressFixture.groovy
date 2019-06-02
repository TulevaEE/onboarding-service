package ee.tuleva.onboarding.user.address

class AddressFixture {

    static addressFixture() {
        return Address.builder()
            .street("Telliskivi 123")
            .countryCode("US")
            .postalCode("99999")
            .districtCode("0123")
    }

}
