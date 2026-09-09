package ee.tuleva.onboarding.nudge

import spock.lang.Specification

import static ee.tuleva.onboarding.nudge.Known.NO
import static ee.tuleva.onboarding.nudge.Known.UNKNOWN
import static ee.tuleva.onboarding.nudge.Known.YES
import static ee.tuleva.onboarding.nudge.NudgeInputsFixture.everythingSorted
import static ee.tuleva.onboarding.nudge.NudgeInputsFixture.sampleFeeComparison
import static ee.tuleva.onboarding.nudge.NudgeKey.*

class NudgeRulesSpec extends Specification {

  def "a saver with everything in place gets no nudge"() {
    expect:
    NudgeRules.decide(everythingSorted().build(), NudgeContext.THIRD_PILLAR_PAYMENT) == NudgeDecision.of(NONE)
  }

  def "the paid account's missing standing order comes first, but only after a savings fund payment"() {
    given:
    def inputs = everythingSorted().accountRecurring(NO).member(false).build()

    expect:
    NudgeRules.decide(inputs, NudgeContext.SAVINGS_FUND_PAYMENT) == NudgeDecision.of(ACCOUNT_RECURRING)
    NudgeRules.decide(inputs, NudgeContext.THIRD_PILLAR_PAYMENT) == NudgeDecision.of(MEMBERSHIP)
  }

  def "a company payer gets the company's standing order nudge or nothing personal"() {
    given:
    def company = everythingSorted().actingAsLegalEntity(true).member(false).secondPillarActive(false)

    expect:
    NudgeRules.decide(company.accountRecurring(NO).build(), NudgeContext.SAVINGS_FUND_PAYMENT) == NudgeDecision.of(ACCOUNT_RECURRING)
    NudgeRules.decide(company.accountRecurring(YES).build(), NudgeContext.SAVINGS_FUND_PAYMENT) == NudgeDecision.of(NONE)
  }

  def "second pillar transfer: #description"() {
    given:
    def inputs = everythingSorted()
        .secondPillarActive(secondPillarActive)
        .secondPillarPartiallyConverted(partially)
        .secondPillarFullyConverted(fully)
        .secondPillarFee(fee)
        .feeComparison(sampleFeeComparison())
        .build()

    expect:
    NudgeRules.decide(inputs, NudgeContext.THIRD_PILLAR_PAYMENT) == expected

    where:
    description                              | secondPillarActive | partially | fully | fee    || expected
    "no second pillar at all"                | false              | false     | false | null   || NudgeDecision.secondPillarTransfer(null)
    "nothing at Tuleva yet"                  | true               | false     | false | 0.0029 || NudgeDecision.secondPillarTransfer(null)
    "partially at Tuleva, high fee"          | true               | true      | false | 0.0065 || NudgeDecision.secondPillarTransfer(sampleFeeComparison())
    "partially at Tuleva, low fee"           | true               | true      | false | 0.0029 || NudgeDecision.of(NONE)
    "fully at Tuleva"                        | true               | true      | true  | 0.0065 || NudgeDecision.of(NONE)
  }

  def "a pending second pillar transfer only suppresses the transfer nudge and falls through"() {
    given:
    def inputs = everythingSorted()
        .secondPillarFullyConverted(false)
        .secondPillarFee(0.0065)
        .canIncreasePaymentRate(true)

    expect:
    NudgeRules.decide(inputs.pendingSecondPillarTransfer(true).build(), NudgeContext.THIRD_PILLAR_PAYMENT) == NudgeDecision.of(SECOND_PILLAR_PAYMENT_RATE)
    NudgeRules.decide(inputs.pendingSecondPillarWithdrawal(true).build(), NudgeContext.THIRD_PILLAR_PAYMENT) == NudgeDecision.of(NONE)
  }

  def "retirement age and minority stop the second pillar nudges"() {
    given:
    def inputs = everythingSorted().secondPillarActive(false).canIncreasePaymentRate(true)

    expect:
    NudgeRules.decide(inputs.reachedRetirementAge(true).build(), NudgeContext.THIRD_PILLAR_PAYMENT) == NudgeDecision.of(NONE)
    NudgeRules.decide(inputs.adult(false).build(), NudgeContext.THIRD_PILLAR_PAYMENT) == NudgeDecision.of(NONE)
  }

  def "leaver status: #description"() {
    given:
    def inputs = everythingSorted()
        .secondPillarActive(false)
        .canIncreasePaymentRate(true)
        .savesInSavingsFund(NO)
        .member(member)
        .leftSecondPillar(leftSecondPillar)
        .build()

    expect:
    NudgeRules.decide(inputs, NudgeContext.THIRD_PILLAR_PAYMENT) == expected

    where:
    description                                   | leftSecondPillar | member || expected
    "a leaver gets no second pillar nudges"       | YES              | true   || NudgeDecision.savingsFund(0.28)
    "unknown skips second pillar and savings"     | UNKNOWN          | true   || NudgeDecision.of(NONE)
    "unknown still allows the membership nudge"   | UNKNOWN          | false  || NudgeDecision.of(MEMBERSHIP)
    "known non-leaver gets the transfer nudge"    | NO               | true   || NudgeDecision.secondPillarTransfer(null)
  }

  def "payment rate: #description"() {
    given:
    def inputs = everythingSorted().canIncreasePaymentRate(true).build()

    expect:
    NudgeRules.decide(inputs, context) == expected

    where:
    description                                  | context                    || expected
    "raise the rate when below the maximum"      | NudgeContext.THIRD_PILLAR_PAYMENT       || NudgeDecision.of(SECOND_PILLAR_PAYMENT_RATE)
    "not right after a rate change"              | NudgeContext.SECOND_PILLAR_PAYMENT_RATE || NudgeDecision.of(NONE)
    "allowed right after a second pillar mandate"| NudgeContext.SECOND_PILLAR_MANDATE      || NudgeDecision.of(SECOND_PILLAR_PAYMENT_RATE)
  }

  def "the second pillar fee review is allowed after a rate change but not after a second pillar mandate"() {
    given:
    def inputs = everythingSorted()
        .secondPillarFullyConverted(false)
        .secondPillarFee(0.0065)
        .feeComparison(sampleFeeComparison())
        .build()

    expect:
    NudgeRules.decide(inputs, NudgeContext.SECOND_PILLAR_PAYMENT_RATE) == NudgeDecision.secondPillarTransfer(sampleFeeComparison())
    NudgeRules.decide(inputs, NudgeContext.SECOND_PILLAR_MANDATE) == NudgeDecision.of(NONE)
  }

  def "third pillar: #description"() {
    given:
    def inputs = everythingSorted()
        .thirdPillarActive(active)
        .thirdPillarPartiallyConverted(partially)
        .thirdPillarFullyConverted(fully)
        .thirdPillarFee(fee)
        .build()

    expect:
    NudgeRules.decide(inputs, context) == expected

    where:
    description                          | active | partially | fully | fee    | context              || expected
    "start a third pillar"               | false  | false     | false | null   | NudgeContext.MEMBERSHIP           || NudgeDecision.of(THIRD_PILLAR_START)
    "not after a third pillar payment"   | false  | false     | false | null   | NudgeContext.THIRD_PILLAR_PAYMENT || NudgeDecision.of(NONE)
    "nothing at Tuleva, check the fees"  | true   | false     | false | 0.0029 | NudgeContext.MEMBERSHIP           || NudgeDecision.of(THIRD_PILLAR_FEES)
    "partially at Tuleva, high fee"      | true   | true      | false | 0.006  | NudgeContext.MEMBERSHIP           || NudgeDecision.of(THIRD_PILLAR_FEES)
    "partially at Tuleva, low fee"       | true   | true      | false | 0.003  | NudgeContext.MEMBERSHIP           || NudgeDecision.of(NONE)
  }

  def "third pillar recurring and raise: #description"() {
    given:
    def inputs = everythingSorted()
        .thirdPillarRecurring(recurring)
        .taxHeadroom(headroom)
        .savesInSavingsFund(NO)
        .member(member)
        .build()

    expect:
    NudgeRules.decide(inputs, context) == expected

    where:
    description                                        | recurring | headroom | member | context                             || expected
    "no standing order yet"                            | NO        | NO       | true   | NudgeContext.THIRD_PILLAR_PAYMENT                || NudgeDecision.of(THIRD_PILLAR_RECURRING)
    "just confirmed a standing order"                  | NO        | NO       | true   | NudgeContext.THIRD_PILLAR_RECURRING_CONFIRMATION || NudgeDecision.savingsFund(0.28)
    "recurring with tax headroom"                      | YES       | YES      | true   | NudgeContext.THIRD_PILLAR_PAYMENT                || NudgeDecision.of(THIRD_PILLAR_RAISE)
    "recurring without headroom moves on"              | YES       | NO       | true   | NudgeContext.THIRD_PILLAR_PAYMENT                || NudgeDecision.savingsFund(0.28)
    "unknown recurring skips recurring, raise, savings"| UNKNOWN   | NO       | true   | NudgeContext.THIRD_PILLAR_PAYMENT                || NudgeDecision.of(NONE)
    "unknown recurring still allows membership"        | UNKNOWN   | NO       | false  | NudgeContext.THIRD_PILLAR_PAYMENT                || NudgeDecision.of(MEMBERSHIP)
    "unknown headroom skips raise and savings"         | YES       | UNKNOWN  | true   | NudgeContext.THIRD_PILLAR_PAYMENT                || NudgeDecision.of(NONE)
  }

  def "savings fund: #description"() {
    given:
    def inputs = everythingSorted()
        .savesInSavingsFund(saves)
        .ownSavingsFundSaver(ownSaver)
        .ownSavingsFundRecurring(ownRecurring)
        .build()

    expect:
    NudgeRules.decide(inputs, context) == expected

    where:
    description                                       | saves   | ownSaver | ownRecurring | context              || expected
    "not a saver anywhere"                            | NO      | NO       | NO           | NudgeContext.THIRD_PILLAR_PAYMENT || NudgeDecision.savingsFund(0.28)
    "not after a savings fund payment"                | NO      | NO       | NO           | NudgeContext.SAVINGS_FUND_PAYMENT || NudgeDecision.of(NONE)
    "unknown saver status skips the savings nudges"   | UNKNOWN | UNKNOWN  | NO           | NudgeContext.THIRD_PILLAR_PAYMENT || NudgeDecision.of(NONE)
    "own account without a standing order"            | YES     | YES      | NO           | NudgeContext.THIRD_PILLAR_PAYMENT || NudgeDecision.of(SAVINGS_FUND_RECURRING)
    "saves only for a child, no nudge about own account"| YES   | NO       | NO           | NudgeContext.THIRD_PILLAR_PAYMENT || NudgeDecision.of(NONE)
  }

  def "membership comes last and not right after joining"() {
    given:
    def inputs = everythingSorted().member(false).build()

    expect:
    NudgeRules.decide(inputs, NudgeContext.THIRD_PILLAR_PAYMENT) == NudgeDecision.of(MEMBERSHIP)
    NudgeRules.decide(inputs, NudgeContext.MEMBERSHIP) == NudgeDecision.of(NONE)
  }
}
