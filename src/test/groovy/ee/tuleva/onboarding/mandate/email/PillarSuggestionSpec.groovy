package ee.tuleva.onboarding.mandate.email

import ee.tuleva.onboarding.conversion.ConversionResponse
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

class PillarSuggestionSpec extends Specification {

  User user = Mock()
  ContactDetails contactDetails = Mock()
  ConversionResponse conversion = Mock()
  PaymentRates paymentRates = Mock()

  def "suggests second pillar"() {
    when:
    contactDetails.isSecondPillarActive() >> secondPillarActive
    conversion.isSecondPillarPartiallyConverted() >> secondPillarPartiallyConverted
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    then:
    pillarSuggestion.isSuggestSecondPillar() == suggestSecondPillar

    where:
    secondPillarActive | secondPillarPartiallyConverted | suggestSecondPillar
    false              | false                          | true
    true               | false                          | true
    false              | true                           | true
    true               | true                           | false
  }

  def "suggests third pillar"() {
    when:
    contactDetails.isThirdPillarActive() >> thirdPillarActive
    conversion.isThirdPillarPartiallyConverted() >> thirdPillarPartiallyConverted
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    then:
    pillarSuggestion.isSuggestThirdPillar() == suggestThirdPillar

    where:
    thirdPillarActive | thirdPillarPartiallyConverted | suggestThirdPillar
    false             | false                         | true
    true              | false                         | true
    false             | true                          | true
    true              | true                          | false
  }

  def "suggests membership"() {
    when:
    user.isMember() >> isMember
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    then:
    pillarSuggestion.isSuggestMembership() == suggestMembership

    where:
    isMember | suggestMembership
    false    | true
    true     | false

  }
}
