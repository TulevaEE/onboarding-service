package ee.tuleva.onboarding.mandate


import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.paymentrate.PaymentRatesFixture.samplePaymentRates

class PillarSuggestionFixture {

  public static secondPillarSuggestion =
      new PillarSuggestion(sampleUser, true, true, notFullyConverted(), samplePaymentRates())

  public static thirdPillarSuggestion =
      new PillarSuggestion(sampleUser, true, true, notFullyConverted(), samplePaymentRates())
}
