package ee.tuleva.onboarding.mandate.details

import spock.lang.Specification

import static ee.tuleva.onboarding.applicationtype.ApplicationType.PARTIAL_WITHDRAWAL
import static ee.tuleva.onboarding.applicationtype.ApplicationType.WITHDRAWAL_THIRD_PILLAR
import static ee.tuleva.onboarding.mandate.MandateFixture.aPartialWithdrawalMandateDetails
import static ee.tuleva.onboarding.mandate.MandateFixture.aThirdPillarPartialWithdrawalMandateDetails

class PartialWithdrawalMandateDetailsSpec extends Specification {

  def "getApplicationType maps the pillar to the partial withdrawal application type"() {
    expect:
    aPartialWithdrawalMandateDetails.getApplicationType() == PARTIAL_WITHDRAWAL
    aThirdPillarPartialWithdrawalMandateDetails.getApplicationType() == WITHDRAWAL_THIRD_PILLAR
  }
}
