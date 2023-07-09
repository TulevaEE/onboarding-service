package ee.tuleva.onboarding.fund


import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund

class ApiFundResponseFixture {

  static tuleva3rdPillarApiFundResponse() {
    return new ApiFundResponse(tuleva3rdPillarFund(), Locale.ENGLISH)
  }

}
