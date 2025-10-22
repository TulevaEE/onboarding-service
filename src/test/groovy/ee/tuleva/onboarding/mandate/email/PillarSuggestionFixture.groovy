package ee.tuleva.onboarding.mandate.email


import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.paymentrate.PaymentRatesFixture.samplePaymentRates

class PillarSuggestionFixture {

  public static secondPillarSuggestion =
      new PillarSuggestion(sampleUser, contactDetailsFixture(), notFullyConverted(), samplePaymentRates())

  public static thirdPillarSuggestion =
      new PillarSuggestion(sampleUser, contactDetailsFixture(), notFullyConverted(), samplePaymentRates())
}
