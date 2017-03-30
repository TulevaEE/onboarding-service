package ee.tuleva.onboarding.mandate.statistics

import ee.tuleva.onboarding.mandate.MandateFixture

class FundValueStatisticsFixture {

    public static List<FundValueStatistics> sampleFundValueStatisticsList() {
        return Arrays.asList(
                FundValueStatistics.builder()
                        .value(40000)
                        .isin(MandateFixture.sampleMandate().fundTransferExchanges.get(0).sourceFundIsin)
                        .build(),
                FundValueStatistics.builder()
                        .value(40000)
                        .isin(MandateFixture.sampleMandate().fundTransferExchanges.get(1).sourceFundIsin)
                        .build(),
                FundValueStatistics.builder()
                        .value(40000)
                        .isin(MandateFixture.sampleMandate().fundTransferExchanges.get(2).sourceFundIsin)
                        .build()
        )
    }
}
