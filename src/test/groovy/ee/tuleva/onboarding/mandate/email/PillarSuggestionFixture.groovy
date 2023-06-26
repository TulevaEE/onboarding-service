package ee.tuleva.onboarding.mandate.email


import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture

class PillarSuggestionFixture {

  public static secondPillarSuggestion =
      new PillarSuggestion(2, sampleUser, contactDetailsFixture(), notFullyConverted())

  public static thirdPillarSuggestion =
      new PillarSuggestion(3, sampleUser, contactDetailsFixture(), notFullyConverted())
}
