package ee.tuleva.onboarding.applicationtype

import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.applicationtype.ApplicationType.*

class ApplicationTypeSpec extends Specification {

  @Unroll
  def "#type withdrawal=#withdrawal fundPensionOpening=#fundPensionOpening transfer=#transfer paymentRate=#paymentRate"() {
    expect:
    type.isWithdrawal() == withdrawal
    type.isFundPensionOpening() == fundPensionOpening
    type.isTransfer() == transfer
    type.isPaymentRate() == paymentRate

    where:
    type                               | withdrawal | fundPensionOpening | transfer | paymentRate
    TRANSFER                           | false      | false               | true     | false
    SELECTION                          | false      | false               | false    | false
    EARLY_WITHDRAWAL                   | true       | false               | false    | false
    WITHDRAWAL                         | true       | false               | false    | false
    CANCELLATION                       | false      | false               | false    | false
    PAYMENT                            | false      | false               | false    | false
    PAYMENT_RATE                       | false      | false               | false    | true
    FUND_PENSION_OPENING               | false      | true                | false    | false
    FUND_PENSION_OPENING_THIRD_PILLAR  | false      | true                | false    | false
    PARTIAL_WITHDRAWAL                 | true       | false               | false    | false
    WITHDRAWAL_THIRD_PILLAR            | true       | false               | false    | false
    SAVING_FUND_PAYMENT                | false      | false               | false    | false
    SAVING_FUND_WITHDRAWAL             | false      | false               | false    | false
  }
}
