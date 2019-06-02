package ee.tuleva.onboarding.user.address

class AddressFixture {

    static addressFixture() {
        return Address.builder()
            .street("Telliskivi 60")
            .countryCode("EE")
            .postalCode("10412")
            .districtCode("0784")
    }

}
