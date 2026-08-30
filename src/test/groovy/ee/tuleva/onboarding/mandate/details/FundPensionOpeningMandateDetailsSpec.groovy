package ee.tuleva.onboarding.mandate.details

import spock.lang.Specification

import static ee.tuleva.onboarding.applicationtype.ApplicationType.FUND_PENSION_OPENING
import static ee.tuleva.onboarding.applicationtype.ApplicationType.FUND_PENSION_OPENING_THIRD_PILLAR
import static ee.tuleva.onboarding.mandate.MandateFixture.aFundPensionOpeningMandateDetails
import static ee.tuleva.onboarding.mandate.MandateFixture.aThirdPillarFundPensionOpeningMandateDetails

class FundPensionOpeningMandateDetailsSpec extends Specification {

  def "getApplicationType maps the pillar to the fund pension opening application type"() {
    expect:
    aFundPensionOpeningMandateDetails.getApplicationType() == FUND_PENSION_OPENING
    aThirdPillarFundPensionOpeningMandateDetails.getApplicationType() == FUND_PENSION_OPENING_THIRD_PILLAR
  }
}
