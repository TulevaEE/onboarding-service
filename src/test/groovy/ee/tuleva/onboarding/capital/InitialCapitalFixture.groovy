package ee.tuleva.onboarding.capital

import ee.tuleva.onboarding.user.User

import static ee.tuleva.onboarding.capital.InitialCapital.*

class InitialCapitalFixture {

    static InitialCapitalBuilder initialCapitalFixture(User forUser) {
        return builder()
                .user(forUser)
                .amount(1000.0)
                .currency("EUR")
                .ownershipFraction(0.0000_0001)
    }
}
