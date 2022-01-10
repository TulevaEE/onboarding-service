package ee.tuleva.onboarding.mandate.email

import ee.tuleva.onboarding.conversion.ConversionResponse
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

class PillarSuggestionSpec extends Specification {

  def user = Mock(User)
  def contactDetails = Mock(ContactDetails)
  def conversion = Mock(ConversionResponse)

  def "suggests second pillar"() {
    when:
    contactDetails.isSecondPillarActive() >> secondPillarActive
    conversion.isSecondPillarFullyConverted() >> secondPillarConverted
    conversion.isSecondPillarSelected() >> secondPillarSelected
    def pillarSuggestion = new PillarSuggestion(3, user, contactDetails, conversion)

    then:
    pillarSuggestion.isSuggestPillar() == suggestPillar

    where:
    secondPillarActive | secondPillarConverted | secondPillarSelected | suggestPillar
    false              | false                 | false                | true
    true               | false                 | false                | true
    true               | false                 | true                 | false
    true               | true                  | true                 | false
  }

  def "suggests third pillar"() {
    when:
    contactDetails.isThirdPillarActive() >> thirdPillarActive
    conversion.isThirdPillarFullyConverted() >> thirdPillarConverted
    def pillarSuggestion = new PillarSuggestion(2, user, contactDetails, conversion)

    then:
    pillarSuggestion.isSuggestPillar() == suggestPillar

    where:
    thirdPillarActive | thirdPillarConverted | suggestPillar
    false             | false                | true
    true              | false                | true
    false             | true                 | true
    true              | true                 | false
  }

  def "suggests membership"() {
    when:
    user.isMember() >> isMember
    contactDetails.isSecondPillarActive() >> secondPillarActive
    conversion.isSecondPillarFullyConverted() >> secondPillarConverted
    def pillarSuggestion = new PillarSuggestion(3, user, contactDetails, conversion)

    then:
    pillarSuggestion.isSuggestMembership() == suggestMembership

    where:
    secondPillarActive | secondPillarConverted | isMember | suggestMembership
    false              | false                 | false    | false
    false              | false                 | true     | false
    false              | true                  | true     | false
    false              | true                  | false    | false
    true               | false                 | false    | false
    true               | false                 | true     | false
    true               | true                  | false    | true
    true               | true                  | true     | false
  }
}
