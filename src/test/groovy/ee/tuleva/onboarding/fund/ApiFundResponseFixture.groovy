package ee.tuleva.onboarding.fund

import spock.lang.Specification

import static ee.tuleva.onboarding.fund.FundFixture.*

class ApiFundResponseFixture {

  static tuleva3rdPillarApiFundResponse() {
    return new ApiFundResponse(tuleva3rdPillarFund, Locale.ENGLISH)
  }

}
