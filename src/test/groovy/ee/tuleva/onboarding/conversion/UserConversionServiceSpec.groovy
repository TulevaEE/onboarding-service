package ee.tuleva.onboarding.conversion

import ee.tuleva.onboarding.account.AccountStatementFixture
import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.auth.PersonFixture
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundManager
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.MandateApplicationStatus
import ee.tuleva.onboarding.mandate.transfer.TransferExchange
import ee.tuleva.onboarding.mandate.transfer.TransferExchangeService
import spock.lang.Specification

class UserConversionServiceSpec extends Specification {

    AccountStatementService accountStatementService = Mock(AccountStatementService)
    TransferExchangeService transferExchangeService = Mock(TransferExchangeService)
    UserConversionService service =
            new UserConversionService(accountStatementService, transferExchangeService)

    final String COVERING_ISN = "SOME ISIN"

    def "GetConversion: Get conversion response for fund selection and transfer"() {
        given:
        1 * accountStatementService.getMyPensionAccountStatement(
                PersonFixture.samplePerson,
                _ as UUID
        ) >> accountBalanceResponse

        1 * transferExchangeService.get(PersonFixture.samplePerson) >> []

        when:
        ConversionResponse conversionResponse = service.getConversion(
                PersonFixture.samplePerson
        )
        then:
        conversionResponse.selectionComplete == selectionComplete
        conversionResponse.transfersComplete == transferComplete

        where:
        accountBalanceResponse                                                       | selectionComplete | transferComplete
        AccountStatementFixture.sampleConvertedFundBalanceWithActiveTulevaFund       | true              | true
        AccountStatementFixture.sampleNonConvertedFundBalanceWithActiveNonTulevaFund | false             | false

    }

    def "GetConversion: Get conversion response for fund transfer given pending mandates cover the lack"() {
        given:
        1 * accountStatementService.getMyPensionAccountStatement(
                PersonFixture.samplePerson,
                _ as UUID
        ) >> accountBalanceResponse

        1 * transferExchangeService.get(PersonFixture.samplePerson) >> sampleTransfersApplicationList

//        1 * fundRepository.findByIsin(_ as String) >>
//                Fund.builder().
//                        isin(COVERING_ISN).
//                        name("Aktsiafond")
//                        .id(123)
//                        .fundManager(
//                        FundManager.builder()
//                                .id(123)
//                                .name(UserConversionService.CONVERTED_FUND_MANAGER_NAME)
//                                .build()
//                ).build()

        when:
        ConversionResponse conversionResponse = service.getConversion(
                PersonFixture.samplePerson
        )
        then:
        conversionResponse.selectionComplete == selectionComplete
        conversionResponse.transfersComplete == transferComplete

        where:
        accountBalanceResponse                                                       | selectionComplete | transferComplete
        AccountStatementFixture.sampleConvertedFundBalanceWithActiveTulevaFund       | true              | true
        AccountStatementFixture.sampleNonConvertedFundBalanceWithActiveNonTulevaFund | false             | true

    }

    List<TransferExchange> sampleTransfersApplicationList = [
            TransferExchange.builder()
                    .status(MandateApplicationStatus.FAILED)
                    .build(),
            TransferExchange.builder()
                    .status(MandateApplicationStatus.COMPLETE)
                    .build(),
            TransferExchange.builder()
                    .status(MandateApplicationStatus.PENDING)
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
                            AccountStatementFixture.sampleNonConvertedFundBalanceWithActiveNonTulevaFund
                                    .first().getFund().getIsin()

                    ).build()
            )
                    .build()
    ]

}
