package ee.tuleva.onboarding.mandate.statistics

import ee.tuleva.onboarding.mandate.MandateFixture
import spock.lang.Specification

class FundTransferStatisticsServiceSpec extends Specification {

    FundTransferStatisticsRepository fundTransferStatisticsRepository = Mock(FundTransferStatisticsRepository)

    FundTransferStatisticsService service = new FundTransferStatisticsService(fundTransferStatisticsRepository)

    def "AddFrom: Add value from Mandate and FundValueStatistics"() {
        given:

        int callCount = MandateFixture.sampleMandate().fundTransferExchanges.size()

        callCount * fundTransferStatisticsRepository
                .findOneByIsin(_ as String) >> sampleFundTransferStatistics()

        int firstValue = sampleFundTransferStatistics().value + sampleFundValueStatisticsList().get(0).value
        int secondValue = firstValue + sampleFundValueStatisticsList().get(1).value
        int thirdValue = secondValue + sampleFundValueStatisticsList().get(2).value

        int firstAmount = sampleFundTransferStatistics().amount + MandateFixture.sampleMandate().fundTransferExchanges.get(0).getAmount()
        int secondAmount = firstAmount + MandateFixture.sampleMandate().fundTransferExchanges.get(1).getAmount()
        int thirdAmount = secondAmount + MandateFixture.sampleMandate().fundTransferExchanges.get(2).getAmount()

        when:
        service.addFrom(MandateFixture.sampleMandate(), sampleFundValueStatisticsList())

        then:

        1 * fundTransferStatisticsRepository.save({FundTransferStatistics fundTransferStatistics ->
            fundTransferStatistics.value == firstValue && fundTransferStatistics.amount == firstAmount
        })

        1 * fundTransferStatisticsRepository.save({FundTransferStatistics fundTransferStatistics ->
            fundTransferStatistics.value == secondValue// && fundTransferStatistics.amount == secondAmount
        })

        1 * fundTransferStatisticsRepository.save({FundTransferStatistics fundTransferStatistics ->
            fundTransferStatistics.value == thirdValue// && fundTransferStatistics.amount == thirdAmount
        })


//        callCount * fundTransferStatisticsRepository.save(_ as FundTransferStatistics)
    }

    List<FundValueStatistics> sampleFundValueStatisticsList() {
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

    FundTransferStatistics sampleFundTransferStatistics() {
        return FundTransferStatistics.builder()
                .isin(MandateFixture.sampleMandate().fundTransferExchanges.get(0).sourceFundIsin)
                .value(20000)
                .amount(2)
                .build()
    }
}
