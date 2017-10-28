package ee.tuleva.onboarding.mandate.statistics

import ee.tuleva.onboarding.epis.account.FundBalance
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.mandate.MandateFixture
import spock.lang.Specification

class FundTransferStatisticsServiceSpec extends Specification {

    FundTransferStatisticsRepository fundTransferStatisticsRepository = Mock(FundTransferStatisticsRepository)
    FundValueStatisticsRepository fundValueStatisticsRepository = Mock(FundValueStatisticsRepository)

    FundTransferStatisticsService service =
            new FundTransferStatisticsService(fundTransferStatisticsRepository, fundValueStatisticsRepository)

    def "AddFrom: Add value from Mandate and FundValueStatistics"() {
        given:

        int callCount = MandateFixture.sampleMandate().fundTransferExchanges.size()

        callCount * fundTransferStatisticsRepository
                .findOneByIsin(_ as String) >> sampleFundTransferStatistics()

        BigDecimal firstValue = sampleFundTransferStatistics().value + FundValueStatisticsFixture.sampleFundValueStatisticsList().get(0).value
        BigDecimal secondValue = firstValue + FundValueStatisticsFixture.sampleFundValueStatisticsList().get(1).value
        BigDecimal thirdValue = secondValue + FundValueStatisticsFixture.sampleFundValueStatisticsList().get(2).value

        BigDecimal firstAmount = sampleFundTransferStatistics().transferred +
                MandateFixture.sampleMandate().fundTransferExchanges.get(0).getAmount() *
                FundValueStatisticsFixture.sampleFundValueStatisticsList().get(0).value

        BigDecimal secondAmount = firstAmount +
                MandateFixture.sampleMandate().fundTransferExchanges.get(1).getAmount() *
                FundValueStatisticsFixture.sampleFundValueStatisticsList().get(1).value

        BigDecimal thirdAmount = secondAmount +
                MandateFixture.sampleMandate().fundTransferExchanges.get(2).getAmount() *
                FundValueStatisticsFixture.sampleFundValueStatisticsList().get(2).value


        when:
        service.addFrom(MandateFixture.sampleMandate(), FundValueStatisticsFixture.sampleFundValueStatisticsList())

        then:

        1 * fundTransferStatisticsRepository.save({FundTransferStatistics fundTransferStatistics ->
            fundTransferStatistics.value == firstValue && fundTransferStatistics.transferred == firstAmount
        })

        1 * fundTransferStatisticsRepository.save({FundTransferStatistics fundTransferStatistics ->
            fundTransferStatistics.value == secondValue && fundTransferStatistics.transferred == secondAmount
        })

        1 * fundTransferStatisticsRepository.save({FundTransferStatistics fundTransferStatistics ->
            fundTransferStatistics.value == thirdValue && fundTransferStatistics.transferred == thirdAmount
        })
    }

    def "AddFrom: Create new fund transfer stats for isin if none existing"() {
        given:

        int callCount = MandateFixture.sampleMandate().fundTransferExchanges.size()

        callCount * fundTransferStatisticsRepository
                .findOneByIsin(_ as String) >> null

        BigDecimal firstValue = BigDecimal.ZERO + FundValueStatisticsFixture.sampleFundValueStatisticsList().get(0).value
        BigDecimal firstAmount = BigDecimal.ZERO +
                MandateFixture.sampleMandate().fundTransferExchanges.get(0).getAmount() *
                FundValueStatisticsFixture.sampleFundValueStatisticsList().get(0).value

        when:
        service.addFrom(MandateFixture.sampleMandate(), FundValueStatisticsFixture.sampleFundValueStatisticsList())

        then:

        1 * fundTransferStatisticsRepository.save({FundTransferStatistics fundTransferStatistics ->
            fundTransferStatistics.value == firstValue && fundTransferStatistics.transferred == firstAmount
        })

    }

    def "saveFundValueStatistics: Saves fund value statistics"() {
        given:
        Fund activeFund = MandateFixture.sampleFunds().get(0)
        BigDecimal activeFundBalanceValue = new BigDecimal(100000)
        FundBalance sampleFundBalance = FundBalance.builder()
                .value(activeFundBalanceValue)
                .fund(activeFund)
                .build()
        FundBalance sampleZeroFundBalance = FundBalance.builder()
                .value(BigDecimal.ZERO)
                .fund(activeFund)
                .build()

        UUID sampleStatisticsIdentifier = UUID.randomUUID()

        checkForFundValueStatisticsSave(sampleFundBalance, sampleStatisticsIdentifier)
        checkForZeroBalanceActiveFundValueStatisticsSave(activeFund, sampleStatisticsIdentifier)

        when:
        service.saveFundValueStatistics([sampleFundBalance, sampleZeroFundBalance], sampleStatisticsIdentifier)

        then:
        true
    }

    private checkForFundValueStatisticsSave(FundBalance sampleFundBalance, UUID sampleStatisticsIdentifier) {
        1 * fundValueStatisticsRepository.save({ FundValueStatistics fundValueStatistics ->
            fundValueStatistics.isin == sampleFundBalance.fund.isin &&
                    fundValueStatistics.value == sampleFundBalance.value &&
                    fundValueStatistics.identifier == sampleStatisticsIdentifier
        })
    }

    private checkForZeroBalanceActiveFundValueStatisticsSave(Fund activeFund, UUID sampleStatisticsIdentifier) {
        1 * fundValueStatisticsRepository.save({ FundValueStatistics fundValueStatistics ->
            fundValueStatistics.isin == activeFund.isin &&
                    fundValueStatistics.value == BigDecimal.ZERO &&
                    fundValueStatistics.identifier == sampleStatisticsIdentifier
        })
    }

    FundTransferStatistics sampleFundTransferStatistics() {
        return FundTransferStatistics.builder()
                .isin(MandateFixture.sampleMandate().fundTransferExchanges.get(0).sourceFundIsin)
                .value(20000)
                .transferred(10000)
                .build()
    }
}
