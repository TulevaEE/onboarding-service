package ee.tuleva.onboarding.mandate.details

import spock.lang.Specification

import static ee.tuleva.onboarding.applicationtype.ApplicationType.CANCELLATION

class WithdrawalCancellationMandateDetailsSpec extends Specification {

  def "getApplicationType is CANCELLATION"() {
    expect:
    new WithdrawalCancellationMandateDetails().getApplicationType() == CANCELLATION
  }
}
