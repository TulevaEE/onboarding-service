package ee.tuleva.onboarding.conversion

import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.manager.FundManager
import ee.tuleva.onboarding.mandate.transfer.TransferExchange
import ee.tuleva.onboarding.mandate.transfer.TransferExchangeService
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.account.AccountStatementFixture.*
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.conversion.UserConversionService.*
import static ee.tuleva.onboarding.epis.mandate.MandateApplicationStatus.*

class UserConversionServiceSpec extends Specification {

    def accountStatementService = Mock(AccountStatementService)
    def transferExchangeService = Mock(TransferExchangeService)
    def service = new UserConversionService(accountStatementService, transferExchangeService)

    @Unroll
    def "GetConversion: Get conversion response for 2nd pillar selection and transfer"() {
        given:
        1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
        transferExchangeService.get(samplePerson) >> []

        when:
        ConversionResponse response = service.getConversion(samplePerson)

        then:
        response.secondPillar.selectionComplete == secondPillarSelectionComplete
        response.secondPillar.transfersComplete == secondPillarTransfersComplete
        !response.thirdPillar.selectionComplete
        response.thirdPillar.transfersComplete

        where:
        accountBalanceResponse             | secondPillarSelectionComplete | secondPillarTransfersComplete
        activeTuleva2ndPillarFundBalance   | true                          | true
        activeExternal2ndPillarFundBalance | false                         | false
        inactiveTuleva2ndPillarFundBalance | false                         | true
        []                                 | false                         | true
    }

    @Unroll
    def "GetConversion: Get conversion response for 3rd pillar selection and transfer"() {
        given:
        1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
        transferExchangeService.get(samplePerson) >> []

        when:
        ConversionResponse response = service.getConversion(samplePerson)

        then:
        response.thirdPillar.selectionComplete == thirdPillarSelectionComplete
        response.thirdPillar.transfersComplete == thirdPillarTransfersComplete
        !response.secondPillar.selectionComplete
        response.secondPillar.transfersComplete

        where:
        accountBalanceResponse             | thirdPillarSelectionComplete | thirdPillarTransfersComplete
        activeTuleva3rdPillarFundBalance   | true                         | true
        activeExternal3rdPillarFundBalance | false                        | false
        inactiveTuleva3rdPillarFundBalance | false                        | true
        []                                 | false                        | true
    }

    @Unroll
    def "GetConversion: Get conversion response for 2nd pillar transfer given pending mandates cover the lack"() {
        given:
        1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
        transferExchangeService.get(samplePerson) >> fullPending2ndPillarApplications

        when:
        ConversionResponse response = service.getConversion(samplePerson)

        then:
        response.secondPillar.selectionComplete == secondPillarSelectionComplete
        response.secondPillar.transfersComplete == secondPillarTransfersComplete

        where:
        accountBalanceResponse             | secondPillarSelectionComplete | secondPillarTransfersComplete
        activeTuleva2ndPillarFundBalance   | true                          | true
        activeExternal2ndPillarFundBalance | false                         | true
    }

    @Unroll
    def "GetConversion 2nd pillar: only full value pending transfer will be marked as covering the lack"() {
        given:
        1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
        transferExchangeService.get(samplePerson) >> partialPending2ndPillarApplications

        when:
        ConversionResponse response = service.getConversion(samplePerson)

        then:
        response.secondPillar.selectionComplete == secondPillarSelectionComplete
        response.secondPillar.transfersComplete == secondPillarTransfersComplete

        where:
        accountBalanceResponse             | secondPillarSelectionComplete | secondPillarTransfersComplete
        activeExternal2ndPillarFundBalance | false                         | false
    }

    @Unroll
    def "GetConversion: Get conversion response for 3rd pillar transfer given pending mandates cover the lack"() {
        given:
        1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
        transferExchangeService.get(samplePerson) >> fullPending3rdPillarApplications

        when:
        ConversionResponse response = service.getConversion(samplePerson)

        then:
        response.thirdPillar.selectionComplete == thirdPillarSelectionComplete
        response.thirdPillar.transfersComplete == thirdPillarTransfersComplete

        where:
        accountBalanceResponse             | thirdPillarSelectionComplete | thirdPillarTransfersComplete
        activeTuleva3rdPillarFundBalance   | true                         | true
        activeExternal3rdPillarFundBalance | false                        | true
    }

    @Unroll
    def "GetConversion 3rd pillar: only full value pending transfer will be marked as covering the lack"() {
        given:
        1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
        transferExchangeService.get(samplePerson) >> partialPending3rdPillarApplications

        when:
        ConversionResponse response = service.getConversion(samplePerson)

        then:
        response.thirdPillar.selectionComplete == thirdPillarSelectionComplete
        response.thirdPillar.transfersComplete == thirdPillarTransfersComplete

        where:
        accountBalanceResponse             | thirdPillarSelectionComplete | thirdPillarTransfersComplete
        activeExternal3rdPillarFundBalance | false                        | false
    }


    List<TransferExchange> fullPending2ndPillarApplications = [
        TransferExchange.builder().status(FAILED).build(),
        TransferExchange.builder().status(COMPLETE).build(),
        TransferExchange.builder()
            .status(PENDING)
            .amount(new BigDecimal(1.0))
            .sourceFund(Fund.builder()
                .isin(activeExternal2ndPillarFundBalance.first().getFund().getIsin())
                .pillar(2)
                .build()
            )
            .targetFund(Fund.builder()
                .isin("EE234")
                .pillar(2)
                .fundManager(FundManager.builder().name(CONVERTED_FUND_MANAGER_NAME).build())
                .build()
            )
            .build()
    ]

    List<TransferExchange> partialPending2ndPillarApplications = [
        TransferExchange.builder().status(FAILED).build(),
        TransferExchange.builder().status(COMPLETE).build(),
        TransferExchange.builder()
            .status(PENDING)
            .amount(0.5)
            .sourceFund(Fund.builder()
                .isin(activeExternal2ndPillarFundBalance.first().getFund().getIsin())
                .pillar(2)
                .build()
            )
            .targetFund(Fund.builder()
                .isin("EE123")
                .pillar(2)
                .fundManager(FundManager.builder().name(CONVERTED_FUND_MANAGER_NAME).build())
                .build()
            )
            .build()
    ]

    List<TransferExchange> fullPending3rdPillarApplications = [
        TransferExchange.builder().status(FAILED).build(),
        TransferExchange.builder().status(COMPLETE).build(),
        TransferExchange.builder()
            .status(PENDING)
            .amount(100.0)
            .sourceFund(activeExternal3rdPillarFundBalance[0].getFund())
            .targetFund(activeExternal3rdPillarFundBalance[1].getFund())
            .build()
    ]

    List<TransferExchange> partialPending3rdPillarApplications = [
        TransferExchange.builder().status(FAILED).build(),
        TransferExchange.builder().status(COMPLETE).build(),
        TransferExchange.builder()
            .status(PENDING)
            .amount(50.0)
            .sourceFund(activeExternal3rdPillarFundBalance[0].getFund())
            .targetFund(activeExternal3rdPillarFundBalance[1].getFund())
            .build()
    ]

}
