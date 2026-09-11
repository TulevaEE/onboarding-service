package ee.tuleva.onboarding.mandate

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class MandateTypeSpec extends Specification {

  def "isWithdrawalType is true only for withdrawal-shaped mandate types: #type"() {
    expect:
    type.isWithdrawalType() == expected

    where:
    type                                            || expected
    MandateType.FUND_PENSION_OPENING                || true
    MandateType.PARTIAL_WITHDRAWAL                  || true
    MandateType.SELECTION                           || false
    MandateType.WITHDRAWAL_CANCELLATION             || false
    MandateType.TRANSFER_CANCELLATION               || false
    MandateType.PAYMENT_RATE_CHANGE                 || false
    MandateType.UNKNOWN                             || false
  }
}
