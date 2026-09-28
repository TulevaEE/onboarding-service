package ee.tuleva.onboarding.investment.cashbuffer

import ee.tuleva.onboarding.ledger.RegistrarPayoutReason
import spock.lang.Specification
import spock.lang.Unroll

class PayoutClassSpec extends Specification {

  @Unroll
  def "a #reason payout is #payoutClass"() {
    expect:
    PayoutClass.of(reason) == payoutClass

    where:
    reason                                                     || payoutClass
    RegistrarPayoutReason.FUND_PENSION                         || PayoutClass.RECURRING
    RegistrarPayoutReason.ONE_OFF_WITHDRAWAL                   || PayoutClass.TAIL
    RegistrarPayoutReason.INHERITANCE                          || PayoutClass.TAIL
    RegistrarPayoutReason.FUND_SWITCH                          || PayoutClass.CYCLE
    RegistrarPayoutReason.SWITCH_TO_PENSION_INVESTMENT_ACCOUNT || PayoutClass.CYCLE
    RegistrarPayoutReason.SECOND_PILLAR_EXIT                   || PayoutClass.CYCLE
    RegistrarPayoutReason.UNRECOGNISED                         || PayoutClass.UNRECOGNISED
  }
}
