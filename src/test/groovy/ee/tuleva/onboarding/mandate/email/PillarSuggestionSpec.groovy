package ee.tuleva.onboarding.mandate.email

import ee.tuleva.onboarding.conversion.ConversionResponse
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import java.util.Optional

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
    paymentRates.canIncrease() >> true
    contactDetails.isSecondPillarActive() >> true
    contactDetails.isThirdPillarActive() >> false
    conversion.isSecondPillarPartiallyConverted() >> false
    conversion.isThirdPillarPartiallyConverted() >> false
    def pillarSuggestion =
        new PillarSuggestion(user, contactDetails, conversion, paymentRates, mandatePillars, false)

    then:
    pillarSuggestion.isSuggestSecondPillar() == suggestSecondPillar
    pillarSuggestion.isSuggestThirdPillar() == suggestThirdPillar

    and:
    pillarSuggestion.isSuggestPaymentRate() == suggestPaymentRate

    where:
    mandatePillars    | suggestSecondPillar | suggestThirdPillar | suggestPaymentRate
    [] as Set         | true                | true               | true
    [2] as Set        | false               | true               | true
    [3] as Set        | true                | false              | true
    [2, 3] as Set     | false               | false              | true
  }

  def "a payment rate change mandate does not nudge a further rate increase"() {
    when:
    user.getAge() >> 40
    paymentRates.canIncrease() >> true
    contactDetails.isSecondPillarActive() >> true
    conversion.isSecondPillarPartiallyConverted() >> true
    conversion.getSecondPillarWeightedAverageFee() >> 0.003
    def pillarSuggestion =
        new PillarSuggestion(
            user, contactDetails, conversion, paymentRates, [2] as Set, false, true, true)

    then:
    !pillarSuggestion.isSuggestPaymentRate()
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

  def "suggests the savings fund only when both pillars are maxed out and the person is not yet a saver"() {
    when:
    user.getAge() >> 40
    contactDetails.isSecondPillarActive() >> true
    contactDetails.isThirdPillarActive() >> true
    conversion.isSecondPillarPartiallyConverted() >> true
    conversion.isThirdPillarPartiallyConverted() >> true
    conversion.getSecondPillarWeightedAverageFee() >> 0.003
    conversion.getThirdPillarWeightedAverageFee() >> 0.003
    paymentRates.canIncrease() >> canIncrease
    def pillarSuggestion =
        new PillarSuggestion(
            user, contactDetails, conversion, paymentRates, [] as Set, false, savesInSavingsFund)

    then:
    pillarSuggestion.isSuggestSavingsFund() == suggestSavingsFund

    where:
    canIncrease | savesInSavingsFund | suggestSavingsFund
    false       | false              | true
    false       | true               | false
    true        | false              | false
  }

  def "does not suggest the savings fund when earlier pillar steps are still open"() {
    when:
    user.getAge() >> 40
    contactDetails.isSecondPillarActive() >> false
    conversion.isSecondPillarPartiallyConverted() >> false
    paymentRates.canIncrease() >> false
    def pillarSuggestion =
        new PillarSuggestion(user, contactDetails, conversion, paymentRates, [] as Set, false, false)

    then:
    !pillarSuggestion.isSuggestSavingsFund()
  }

  def "reports the nudge that actually renders, in chain order"() {
    when:
    user.getAge() >> 40
    user.isMember() >> member
    contactDetails.isSecondPillarActive() >> secondPillarActive
    contactDetails.isThirdPillarActive() >> true
    conversion.isSecondPillarPartiallyConverted() >> secondPillarActive
    conversion.isThirdPillarPartiallyConverted() >> true
    conversion.getSecondPillarWeightedAverageFee() >> 0.003
    conversion.getThirdPillarWeightedAverageFee() >> 0.003
    paymentRates.canIncrease() >> canIncrease
    def pillarSuggestion =
        new PillarSuggestion(
            user, contactDetails, conversion, paymentRates, [] as Set, false, savesInSavingsFund)

    then:
    pillarSuggestion.renderedNudgeTag() == Optional.ofNullable(tag)

    where:
    secondPillarActive | canIncrease | savesInSavingsFund | member | tag
    false              | false       | true               | false  | "nudge_second_pillar"
    true               | true        | true               | false  | "nudge_payment_rate"
    true               | false       | false              | false  | "nudge_savings_fund"
    true               | false       | true               | false  | "nudge_membership"
    true               | false       | true               | true   | null
  }
}
