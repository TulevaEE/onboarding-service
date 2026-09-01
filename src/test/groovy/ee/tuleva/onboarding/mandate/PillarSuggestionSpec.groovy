package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.conversion.ConversionResponse
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import java.util.Optional

class PillarSuggestionSpec extends Specification {

  User user = Mock()
  ConversionResponse conversion = Mock()
  PaymentRates paymentRates = Mock()

  def "suggests second pillar"() {
    when:
    user.getAge() >> 40
    conversion.isSecondPillarPartiallyConverted() >> secondPillarPartiallyConverted
    conversion.getSecondPillarWeightedAverageFee() >> secondPillarWeightedAverageFee
    def pillarSuggestion = new PillarSuggestion(user, secondPillarActive, false, conversion, paymentRates)

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
    conversion.isThirdPillarPartiallyConverted() >> thirdPillarPartiallyConverted
    conversion.getThirdPillarWeightedAverageFee() >> thirdPillarWeightedAverageFee
    def pillarSuggestion = new PillarSuggestion(user, false, thirdPillarActive, conversion, paymentRates)

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

  def "high fees only matter while the pillar is not fully converted"() {
    when:
    conversion.isSecondPillarPartiallyConverted() >> true
    conversion.isSecondPillarFullyConverted() >> fullyConverted
    conversion.getSecondPillarWeightedAverageFee() >> fee
    user.getAge() >> 40
    def pillarSuggestion = new PillarSuggestion(user, true, false, conversion, paymentRates)

    then:
    pillarSuggestion.isSuggestSecondPillar() == suggestSecondPillar

    where:
    fullyConverted | fee   | suggestSecondPillar
    false          | 0.004 | true
    false          | 0.003 | false
    true           | 0.006 | false
  }

  def "suggests membership"() {
    when:
    user.isMember() >> isMember
    def pillarSuggestion = new PillarSuggestion(user, false, false, conversion, paymentRates)

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
    conversion.isSecondPillarPartiallyConverted() >> false
    conversion.isThirdPillarPartiallyConverted() >> false
    def pillarSuggestion =
        new PillarSuggestion(user, true, false, conversion, paymentRates, mandatePillars, false)

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
    conversion.isSecondPillarPartiallyConverted() >> true
    conversion.getSecondPillarWeightedAverageFee() >> 0.003
    def pillarSuggestion =
        new PillarSuggestion(
            user, true, false, conversion, paymentRates, [2] as Set, false, true, true)

    then:
    !pillarSuggestion.isSuggestPaymentRate()
  }

  def "never suggests second pillar steps to someone who has left the second pillar"() {
    when:
    user.getAge() >> 40
    conversion.isSecondPillarPartiallyConverted() >> false
    paymentRates.canIncrease() >> true
    def pillarSuggestion =
        new PillarSuggestion(user, true, false, conversion, paymentRates, [] as Set, leftSecondPillar)

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
    conversion.isSecondPillarPartiallyConverted() >> false
    paymentRates.canIncrease() >> true
    def pillarSuggestion = new PillarSuggestion(user, true, false, conversion, paymentRates)

    then:
    pillarSuggestion.isSuggestSecondPillar() == suggestSecondPillar
    pillarSuggestion.isSuggestPaymentRate() == suggestPaymentRate

    where:
    age | suggestSecondPillar | suggestPaymentRate
    17  | false               | false
    18  | true                | true
  }

  def "never suggests the second pillar to someone at retirement age"() {
    when:
    user.getAge() >> 66
    user.hasReachedRetirementAge() >> true
    conversion.isSecondPillarPartiallyConverted() >> false
    paymentRates.canIncrease() >> true
    def pillarSuggestion = new PillarSuggestion(user, true, false, conversion, paymentRates)

    then:
    !pillarSuggestion.isSuggestSecondPillar()
    pillarSuggestion.isSuggestPaymentRate()
  }

  def "does not suggest a payment rate increase without an active second pillar"() {
    when:
    user.getAge() >> 40
    conversion.isSecondPillarPartiallyConverted() >> false
    paymentRates.canIncrease() >> true
    def pillarSuggestion = new PillarSuggestion(user, false, false, conversion, paymentRates)

    then:
    pillarSuggestion.isSuggestSecondPillar()
    !pillarSuggestion.isSuggestPaymentRate()
  }

  def "suggests the savings fund only when both pillars are maxed out and the person is not yet a saver"() {
    when:
    user.getAge() >> 40
    conversion.isSecondPillarPartiallyConverted() >> true
    conversion.isThirdPillarPartiallyConverted() >> true
    conversion.getSecondPillarWeightedAverageFee() >> 0.003
    conversion.getThirdPillarWeightedAverageFee() >> 0.003
    paymentRates.canIncrease() >> canIncrease
    def pillarSuggestion =
        new PillarSuggestion(
            user, true, true, conversion, paymentRates, [] as Set, false, savesInSavingsFund)

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
    conversion.isSecondPillarPartiallyConverted() >> false
    paymentRates.canIncrease() >> false
    def pillarSuggestion =
        new PillarSuggestion(user, false, false, conversion, paymentRates, [] as Set, false, false)

    then:
    !pillarSuggestion.isSuggestSavingsFund()
  }

  def "suggests raising the third pillar contribution only to regular savers with tax headroom"() {
    when:
    user.getAge() >> age
    conversion.isSecondPillarPartiallyConverted() >> true
    conversion.isThirdPillarPartiallyConverted() >> true
    conversion.getSecondPillarWeightedAverageFee() >> 0.003
    conversion.getThirdPillarWeightedAverageFee() >> 0.003
    paymentRates.canIncrease() >> false
    def pillarSuggestion =
        new PillarSuggestion(
            user, true, true, conversion, paymentRates, [] as Set, false, false, false,
            new RecurringPayments(thirdPillarRecurring, true), taxHeadroom)

    then:
    pillarSuggestion.isSuggestThirdPillarRaise() == suggestRaise
    pillarSuggestion.isSuggestSavingsFund() == suggestSavingsFund

    where:
    age | thirdPillarRecurring | taxHeadroom | suggestRaise | suggestSavingsFund
    40  | true                 | true        | true         | false
    40  | true                 | false       | false        | true
    40  | false                | true        | false        | false
    17  | true                 | true        | false        | false
  }

  def "reports the raise nudge between the recurring and savings fund nudges"() {
    when:
    user.getAge() >> 40
    conversion.isSecondPillarPartiallyConverted() >> true
    conversion.isThirdPillarPartiallyConverted() >> true
    conversion.getSecondPillarWeightedAverageFee() >> 0.003
    conversion.getThirdPillarWeightedAverageFee() >> 0.003
    paymentRates.canIncrease() >> false
    def pillarSuggestion =
        new PillarSuggestion(
            user, true, true, conversion, paymentRates, [] as Set, false, false, false,
            new RecurringPayments(true, true), true)

    then:
    pillarSuggestion.renderedNudgeTag() == Optional.of("nudge_third_pillar_raise")
  }

  def "reports the third pillar nudge when only the third pillar step is open"() {
    when:
    user.getAge() >> 40
    def pillarSuggestion =
        new PillarSuggestion(user, false, false, conversion, paymentRates, [2] as Set, false)

    then:
    pillarSuggestion.renderedNudgeTag() == Optional.of("nudge_third_pillar")
  }

  def "reports the third pillar recurring nudge when both pillar steps are blocked"() {
    when:
    user.getAge() >> 40
    def pillarSuggestion =
        new PillarSuggestion(
            user, false, true, conversion, paymentRates, [2, 3] as Set, false, false, false,
            new RecurringPayments(false, true))

    then:
    pillarSuggestion.renderedNudgeTag() == Optional.of("nudge_third_pillar_recurring")
  }

  def "reports the savings fund recurring nudge for a non-recurring saver with both pillar steps blocked"() {
    when:
    user.getAge() >> 40
    def pillarSuggestion =
        new PillarSuggestion(
            user, false, false, conversion, paymentRates, [2, 3] as Set, false, true, false,
            new RecurringPayments(true, false))

    then:
    pillarSuggestion.renderedNudgeTag() == Optional.of("nudge_savings_fund_recurring")
  }

  def "reports the nudge that actually renders, in chain order"() {
    when:
    user.getAge() >> 40
    user.isMember() >> member
    conversion.isSecondPillarPartiallyConverted() >> secondPillarActive
    conversion.isThirdPillarPartiallyConverted() >> true
    conversion.getSecondPillarWeightedAverageFee() >> 0.003
    conversion.getThirdPillarWeightedAverageFee() >> 0.003
    paymentRates.canIncrease() >> canIncrease
    def pillarSuggestion =
        new PillarSuggestion(
            user, secondPillarActive, true, conversion, paymentRates, [] as Set, false, savesInSavingsFund)

    then:
    pillarSuggestion.renderedNudgeTag() == Optional.ofNullable(tag)

    where:
    secondPillarActive | canIncrease | savesInSavingsFund | member | tag
    false              | false       | true               | false  | "nudge_second_pillar"
    true               | true        | true               | false  | "nudge_payment_rate"
    true               | false       | false              | false  | "nudge_savings_fund"
    true               | false       | true               | false  | "nudge_membership"
    true               | false       | true               | true   | "nudge_none"
  }
}
