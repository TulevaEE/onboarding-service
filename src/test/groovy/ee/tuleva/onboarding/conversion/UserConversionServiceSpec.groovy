package ee.tuleva.onboarding.conversion

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.manager.FundManager
import ee.tuleva.onboarding.mandate.transfer.TransferExchange
import ee.tuleva.onboarding.mandate.transfer.TransferExchangeService
import spock.lang.Specification

import static ee.tuleva.onboarding.account.AccountStatementFixture.*
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.mandate.MandateApplicationStatus.*

class UserConversionServiceSpec extends Specification {

    EpisService episService = Mock(EpisService)
    TransferExchangeService transferExchangeService = Mock(TransferExchangeService)
    UserConversionService service =
            new UserConversionService(episService, transferExchangeService)

    final String COVERING_ISN = "SOME ISIN"

    def "GetConversion: Get conversion response for fund selection and transfer"() {
        given:
        1 * episService.getAccountStatement(samplePerson) >> accountBalanceResponse

        1 * transferExchangeService.get(samplePerson) >> []

        when:
        ConversionResponse conversionResponse = service.getConversion(
                samplePerson
        )
        then:
        conversionResponse.selectionComplete == selectionComplete
        conversionResponse.transfersComplete == transferComplete

        where:
        accountBalanceResponse                               | selectionComplete | transferComplete
        sampleConvertedFundBalanceWithActiveTulevaFund       | true              | true
        sampleNonConvertedFundBalanceWithActiveNonTulevaFund | false             | false
        sampleConvertedFundBalanceWithNonActiveTulevaFund    | false             | true

    }

    def "GetConversion: Get conversion response for fund transfer given pending mandates cover the lack"() {
        given:
        1 * episService.getAccountStatement(samplePerson) >> accountBalanceResponse

        1 * transferExchangeService.get(samplePerson) >> sampleTransfersApplicationListWithFullPendingTransferCoverage

        when:
        ConversionResponse conversionResponse = service.getConversion(
                samplePerson
        )
        then:
        conversionResponse.selectionComplete == selectionComplete
        conversionResponse.transfersComplete == transferComplete

        where:
        accountBalanceResponse                               | selectionComplete | transferComplete
        sampleConvertedFundBalanceWithActiveTulevaFund       | true              | true
        sampleNonConvertedFundBalanceWithActiveNonTulevaFund | false             | true

    }

    def "GetConversion: Only full value pending transfer will be marked as covering the lack"() {
        given:
        1 * episService.getAccountStatement(samplePerson) >> accountBalanceResponse

        1 * transferExchangeService.get(samplePerson) >> sampleTransfersApplicationListWithPartialPendingTransferCoverage

        when:
        ConversionResponse conversionResponse = service.getConversion(samplePerson)
        then:
        conversionResponse.selectionComplete == selectionComplete
        conversionResponse.transfersComplete == transferComplete

        where:
        accountBalanceResponse                               | selectionComplete | transferComplete
        sampleNonConvertedFundBalanceWithActiveNonTulevaFund | false             | false

    }

    List<TransferExchange> sampleTransfersApplicationListWithFullPendingTransferCoverage = [
            TransferExchange.builder()
                    .status(FAILED)
                    .build(),
            TransferExchange.builder()
                    .status(COMPLETE)
                    .build(),
            TransferExchange.builder()
                    .status(PENDING)
                    .amount(new BigDecimal(1.0))
                    .targetFund(
                    Fund.builder()
                            .isin(COVERING_ISN)
                            .fundManager(
                            FundManager.builder()
                                    .name(
                                    UserConversionService.CONVERTED_FUND_MANAGER_NAME
                            ).build()

                    )
                            .build()

            )
                    .sourceFund(
                    Fund.builder().isin(
                            sampleNonConvertedFundBalanceWithActiveNonTulevaFund
                                    .first().getFund().getIsin()

                    ).build()
            )
                    .build()
    ]

    List<TransferExchange> sampleTransfersApplicationListWithPartialPendingTransferCoverage = [
            TransferExchange.builder()
                    .status(FAILED)
                    .build(),
            TransferExchange.builder()
                    .status(COMPLETE)
                    .build(),
            TransferExchange.builder()
                    .status(PENDING)
                    .amount(0.5)
                    .targetFund(
                    Fund.builder()
                            .isin(COVERING_ISN)
                            .fundManager(
                            FundManager.builder()
                                    .name(
                                    UserConversionService.CONVERTED_FUND_MANAGER_NAME
                            ).build()

                    )
                            .build()

            )
                    .sourceFund(
                    Fund.builder().isin(
                            sampleNonConvertedFundBalanceWithActiveNonTulevaFund
                                    .first().getFund().getIsin()

                    ).build()
            )
                    .build()
    ]

}
