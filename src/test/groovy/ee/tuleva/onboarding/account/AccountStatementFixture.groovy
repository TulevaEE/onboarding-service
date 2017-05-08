package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.mandate.MandateFixture

class AccountStatementFixture {

    public static List<FundBalance> sampleFundBalance = FundBalance.builder()
                .value(100)
                .fund(MandateFixture.sampleFunds().first())
                .build()

}
