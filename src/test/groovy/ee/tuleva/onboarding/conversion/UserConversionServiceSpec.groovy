package ee.tuleva.onboarding.conversion

import ee.tuleva.onboarding.account.AccountStatementFixture
import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.auth.PersonFixture
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundManager
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.processor.implementation.EpisService
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.MandateApplicationStatus
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.TransferApplicationDTO
import spock.lang.Specification

class UserConversionServiceSpec extends Specification {

    AccountStatementService accountStatementService = Mock(AccountStatementService)
    EpisService episService = Mock(EpisService)
    FundRepository fundRepository = Mock(FundRepository)

    UserConversionService service =
            new UserConversionService(accountStatementService, episService, fundRepository)

    final String COVERING_ISN = "SOME ISIN"

    def "GetConversion: Get conversion response for fund selection and transfer"() {
        given:
        1 * accountStatementService.getMyPensionAccountStatement(
                PersonFixture.samplePerson,
                _ as UUID
        ) >> accountBalanceResponse

        1 * episService.getTransferApplications(_ as Person) >> []

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

        1 * episService.getTransferApplications(_ as Person) >> sampleTransfersApplicationList

        1 * fundRepository.findByIsin(_ as String) >>
                Fund.builder().
                        isin(COVERING_ISN).
                        name("Aktsiafond")
                        .id(123)
                        .fundManager(
                        FundManager.builder()
                                .id(123)
                                .name(UserConversionService.CONVERTED_FUND_MANAGER_NAME)
                                .build()
                ).build()

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

    List<TransferApplicationDTO> sampleTransfersApplicationList = [
            TransferApplicationDTO.builder()
                    .status(MandateApplicationStatus.FAILED)
                    .build(),
            TransferApplicationDTO.builder()
                    .status(MandateApplicationStatus.COMPLETE)
                    .build(),
            TransferApplicationDTO.builder()
                    .status(MandateApplicationStatus.PENDING)
                    .targetFundIsin(COVERING_ISN)
                    .sourceFundIsin(
                    AccountStatementFixture.sampleNonConvertedFundBalanceWithActiveNonTulevaFund
                            .first().getFund().getIsin()
            )
                    .build()
    ]

}
