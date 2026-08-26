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
    user.getAge() >> 40
    contactDetails.isSecondPillarActive() >> secondPillarActive
    conversion.isSecondPillarPartiallyConverted() >> secondPillarPartiallyConverted
    conversion.getSecondPillarWeightedAverageFee() >> secondPillarWeightedAverageFee
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    then:
    pillarSuggestion.isSuggestSecondPillar() == suggestSecondPillar

    where:
    secondPillarActive | secondPillarPartiallyConverted | secondPillarWeightedAverageFee | suggestSecondPillar
    false              | false                          | null                           | true
    true               | false                          | null                           | true
    false              | true                           | null                           | true
    true               | true                           | 0.003                          | false
    true               | true                           | 0.006                          | true
  }

  def "suggests third pillar"() {
    when:
    contactDetails.isThirdPillarActive() >> thirdPillarActive
    conversion.isThirdPillarPartiallyConverted() >> thirdPillarPartiallyConverted
    conversion.getThirdPillarWeightedAverageFee() >> thirdPillarWeightedAverageFee
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    then:
    pillarSuggestion.isSuggestThirdPillar() == suggestThirdPillar

    where:
    thirdPillarActive | thirdPillarPartiallyConverted | thirdPillarWeightedAverageFee | suggestThirdPillar
    false             | false                         | null                          | true
    true              | false                         | null                          | true
    false             | true                          | null                          | true
    true              | true                          | 0.003                         | false
    true              | true                          | 0.006                         | true
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

  def "never suggests the pillar the email itself concerns"() {
    when:
    user.getAge() >> 40
    contactDetails.isSecondPillarActive() >> false
    contactDetails.isThirdPillarActive() >> false
    conversion.isSecondPillarPartiallyConverted() >> false
    conversion.isThirdPillarPartiallyConverted() >> false
    def pillarSuggestion =
        new PillarSuggestion(user, contactDetails, conversion, paymentRates, mandatePillars, false)

    then:
    pillarSuggestion.isSuggestSecondPillar() == suggestSecondPillar
    pillarSuggestion.isSuggestThirdPillar() == suggestThirdPillar

    where:
    mandatePillars    | suggestSecondPillar | suggestThirdPillar
    [] as Set         | true                | true
    [2] as Set        | false               | true
    [3] as Set        | true                | false
    [2, 3] as Set     | false               | false
  }

  def "never suggests second pillar steps to someone who has left the second pillar"() {
    when:
    user.getAge() >> 40
    contactDetails.isSecondPillarActive() >> true
    conversion.isSecondPillarPartiallyConverted() >> false
    paymentRates.canIncrease() >> true
    def pillarSuggestion =
        new PillarSuggestion(user, contactDetails, conversion, paymentRates, [] as Set, leftSecondPillar)

    then:
    pillarSuggestion.isSuggestSecondPillar() == suggestSecondPillar
    pillarSuggestion.isSuggestPaymentRate() == suggestPaymentRate
    pillarSuggestion.isLeftSecondPillar() == leftSecondPillar

    where:
    leftSecondPillar | suggestSecondPillar | suggestPaymentRate
    false            | true                | true
    true             | false               | false
  }

  def "never suggests second pillar steps to an underage person"() {
    when:
    user.getAge() >> age
    contactDetails.isSecondPillarActive() >> true
    conversion.isSecondPillarPartiallyConverted() >> false
    paymentRates.canIncrease() >> true
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    then:
    pillarSuggestion.isSuggestSecondPillar() == suggestSecondPillar
    pillarSuggestion.isSuggestPaymentRate() == suggestPaymentRate

    where:
    age | suggestSecondPillar | suggestPaymentRate
    17  | false               | false
    18  | true                | true
  }

  def "does not suggest a payment rate increase without an active second pillar"() {
    when:
    user.getAge() >> 40
    contactDetails.isSecondPillarActive() >> false
    conversion.isSecondPillarPartiallyConverted() >> false
    paymentRates.canIncrease() >> true
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)

    then:
    pillarSuggestion.isSuggestSecondPillar()
    !pillarSuggestion.isSuggestPaymentRate()
  }
}
