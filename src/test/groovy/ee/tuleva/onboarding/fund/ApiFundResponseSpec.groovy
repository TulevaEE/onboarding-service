package ee.tuleva.onboarding.fund

import spock.lang.Specification

import static ee.tuleva.onboarding.fund.FundFixture.lhv3rdPillarFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund

class ApiFundResponseSpec extends Specification {

  def "own fund follows the fund manager"() {
    expect:
    new ApiFundResponse(tuleva3rdPillarFund(), Locale.ENGLISH).isOwnFund()
    !new ApiFundResponse(lhv3rdPillarFund(), Locale.ENGLISH).isOwnFund()
  }
}
