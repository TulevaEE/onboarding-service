package ee.tuleva.onboarding.conversion

import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.account.CashFlowService
import ee.tuleva.onboarding.epis.cashflows.CashFlow
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement
import ee.tuleva.onboarding.fund.ApiFundResponse
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.fund.manager.FundManager
import ee.tuleva.onboarding.mandate.application.Application
import ee.tuleva.onboarding.mandate.application.ApplicationService
import ee.tuleva.onboarding.mandate.application.Exchange
import ee.tuleva.onboarding.mandate.application.TransferApplicationDetails
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import static ee.tuleva.onboarding.account.AccountStatementFixture.*
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.*
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING

class UserConversionServiceSpec extends Specification {

  def accountStatementService = Mock(AccountStatementService)
  def cashFlowService = Mock(CashFlowService)
  def fundRepository = Mock(FundRepository)
  def applicationService = Mock(ApplicationService)
  def clock = Clock.fixed(Instant.parse("2019-12-30T10:06:01Z"), ZoneOffset.UTC)

  def service = new UserConversionService(accountStatementService, cashFlowService,
      fundRepository, clock, applicationService)

  def "GetConversion: Get conversion response for 2nd pillar withdrawal"() {
    given:
    accountStatementService.getAccountStatement(samplePerson) >> []
    applicationService.getTransferApplications(PENDING, samplePerson) >> []
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()
    applicationService.hasPendingWithdrawals(samplePerson) >> true

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.secondPillar.pendingWithdrawal
    !response.thirdPillar.pendingWithdrawal
  }

  def "GetConversion: Get conversion response for 2nd pillar selection and transfer"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> []
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.secondPillar.selectionComplete == secondPillarSelectionComplete
    response.secondPillar.transfersComplete == secondPillarTransfersComplete
    !response.secondPillar.pendingWithdrawal
    !response.thirdPillar.selectionComplete
    response.thirdPillar.transfersComplete
    !response.thirdPillar.pendingWithdrawal

    where:
    accountBalanceResponse             | secondPillarSelectionComplete | secondPillarTransfersComplete
    activeTuleva2ndPillarFundBalance   | true                          | true
    activeExternal2ndPillarFundBalance | false                         | false
    inactiveTuleva2ndPillarFundBalance | false                         | true
    []                                 | false                         | true
  }

  def "GetConversion: Get conversion response for 3rd pillar selection and transfer"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> []

    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

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
    activeTuleva3rdPillarFund          | true                         | true
    activeExternal3rdPillarFundBalance | false                        | false
    inactiveTuleva3rdPillarFundBalance | false                        | true
    []                                 | false                        | true
  }

  def "GetConversion: Get conversion response for 2nd pillar transfer given pending mandates cover the lack"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> [fullPending2ndPillarApplication]
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

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

  def "GetConversion: Get conversion response for 2nd pillar PIK transfer"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> [fullPendingPikApplication]
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.secondPillar.selectionComplete == secondPillarSelectionComplete
    response.secondPillar.transfersComplete == secondPillarTransfersComplete

    where:
    accountBalanceResponse             | secondPillarSelectionComplete | secondPillarTransfersComplete
    activeTuleva2ndPillarFundBalance   | true                          | true
    activeExternal2ndPillarFundBalance | false                         | false
  }

  def "GetConversion 2nd pillar: only full value pending transfer will be marked as covering the lack"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> [partialPending2ndPillarApplication]
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.secondPillar.selectionComplete == secondPillarSelectionComplete
    response.secondPillar.transfersComplete == secondPillarTransfersComplete

    where:
    accountBalanceResponse             | secondPillarSelectionComplete | secondPillarTransfersComplete
    activeExternal2ndPillarFundBalance | false                         | false
  }

  def "GetConversion: Get conversion response for 3rd pillar transfer given pending mandates cover the lack"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> [fullPending3rdPillarApplication]
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

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

  def "GetConversion 3rd pillar: only full value pending transfer will be marked as covering the lack"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> [partialPending3rdPillarApplication]
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.thirdPillar.selectionComplete == thirdPillarSelectionComplete
    response.thirdPillar.transfersComplete == thirdPillarTransfersComplete

    where:
    accountBalanceResponse             | thirdPillarSelectionComplete | thirdPillarTransfersComplete
    activeExternal3rdPillarFundBalance | false                        | false
  }

  def "calculates contribution and subtraction sums"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> []
    applicationService.getTransferApplications(PENDING, samplePerson) >> []
    fundRepository.findByIsin("EE123") >> Fund.builder().pillar(2).build()
    fundRepository.findByIsin("EE234") >> Fund.builder().pillar(3).build()

    cashFlowService.getCashFlowStatement(samplePerson) >> CashFlowStatement.builder()
        .transactions([
            new CashFlow("EE123", LocalDate.parse("2018-12-31"), 100.0, "EUR", CONTRIBUTION_CASH),
            new CashFlow("EE123", LocalDate.parse("2019-01-01"), 1.0, "EUR", CONTRIBUTION_CASH),
            new CashFlow("EE123", LocalDate.parse("2019-11-20"), 1.0, "EUR", CONTRIBUTION_CASH),
            new CashFlow("EE123", LocalDate.parse("2019-12-20"), 1.0, "EUR", SUBTRACTION),
            new CashFlow("EE123", LocalDate.parse("2019-12-21"), 1.0, "EUR", SUBTRACTION),

            new CashFlow("EE234", LocalDate.parse("2018-12-31"), 100.0, "EUR", CONTRIBUTION_CASH),
            new CashFlow("EE234", LocalDate.parse("2019-01-01"), 1.0, "EUR", CONTRIBUTION_CASH),
            new CashFlow("EE234", LocalDate.parse("2019-01-02"), 1.0, "EUR", CONTRIBUTION_CASH),
            new CashFlow("EE234", LocalDate.parse("2019-11-20"), 1.0, "EUR", CONTRIBUTION_CASH),
            new CashFlow("EE234", LocalDate.parse("2019-12-20"), 20.0, "EUR", CONTRIBUTION),
            new CashFlow("EE234", LocalDate.parse("2019-12-20"), 1.0, "EUR", SUBTRACTION),
            new CashFlow("EE234", LocalDate.parse("2019-12-21"), 1.0, "EUR", SUBTRACTION),
        ])
        .build()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    with(response.secondPillar) {
      contribution.yearToDate == 2.0
      contribution.total == 102.0
      subtraction.yearToDate == 2.0
      subtraction.total == 2.0
      paymentComplete == null
    }
    with(response.thirdPillar) {
      contribution.total == 123.0
      contribution.yearToDate == 3.0
      subtraction.yearToDate == 2.0
      subtraction.total == 2.0
      paymentComplete
    }
  }


  Application<TransferApplicationDetails> fullPending2ndPillarApplication =
      Application.<TransferApplicationDetails> builder()
          .status(PENDING)
          .details(
              TransferApplicationDetails.builder()
                  .sourceFund(new ApiFundResponse(Fund.builder()
                      .isin(activeExternal2ndPillarFundBalance.first().getFund().getIsin())
                      .pillar(2)
                      .build(), "en")
                  )
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(Fund.builder()
                              .isin(activeExternal2ndPillarFundBalance.first().getFund().getIsin())
                              .pillar(2)
                              .build(), "en"),
                          new ApiFundResponse(Fund.builder()
                              .isin("EE234")
                              .pillar(2)
                              .fundManager(FundManager.builder().name(FundManager.TULEVA_FUND_MANAGER_NAME).build())
                              .build(), "en"),
                          null,
                          1.0
                      )
                  )
                  .build()
          )
          .build()


  Application<TransferApplicationDetails> partialPending2ndPillarApplication =
      Application.<TransferApplicationDetails> builder()
          .status(PENDING)
          .details(
              TransferApplicationDetails.builder()
                  .sourceFund(new ApiFundResponse(Fund.builder()
                      .isin(activeExternal2ndPillarFundBalance.first().getFund().getIsin())
                      .pillar(2)
                      .build(), "en")
                  )
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(Fund.builder()
                              .isin(activeExternal2ndPillarFundBalance.first().getFund().getIsin())
                              .pillar(2)
                              .build(), "en"),
                          new ApiFundResponse(Fund.builder()
                              .isin("EE234")
                              .pillar(2)
                              .fundManager(FundManager.builder().name(FundManager.TULEVA_FUND_MANAGER_NAME).build())
                              .build(), "en"),
                          null,
                          0.5
                      )
                  )
                  .build()
          )
          .build()

  Application<TransferApplicationDetails> fullPendingPikApplication =
      Application.<TransferApplicationDetails> builder()
          .status(PENDING)
          .details(
              TransferApplicationDetails.builder()
                  .sourceFund(new ApiFundResponse(Fund.builder()
                      .isin(activeTuleva2ndPillarFundBalance.first().getFund().getIsin())
                      .pillar(2)
                      .build(), "en")
                  )
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(Fund.builder()
                              .isin(activeTuleva2ndPillarFundBalance.first().getFund().getIsin())
                              .pillar(2)
                              .build(), "en"),
                          null,
                          "EE801281685311741971",
                          1.0
                      )
                  )
                  .build()
          )
          .build()

  Application<TransferApplicationDetails> fullPending3rdPillarApplication =
      Application.<TransferApplicationDetails> builder()
          .status(PENDING)
          .details(
              TransferApplicationDetails.builder()
                  .sourceFund(new ApiFundResponse(activeExternal3rdPillarFundBalance[0].getFund(), "en"))
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(activeExternal3rdPillarFundBalance[0].getFund(), "en"),
                          new ApiFundResponse(activeExternal3rdPillarFundBalance[1].getFund(), "en"),
                          null,
                          100.0
                      )
                  )
                  .build()
          )
          .build()

  Application<TransferApplicationDetails> partialPending3rdPillarApplication =
      Application.<TransferApplicationDetails> builder()
          .status(PENDING)
          .details(
              TransferApplicationDetails.builder()
                  .sourceFund(new ApiFundResponse(activeExternal3rdPillarFundBalance[0].getFund(), "en"))
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(activeExternal3rdPillarFundBalance[0].getFund(), "en"),
                          new ApiFundResponse(activeExternal3rdPillarFundBalance[1].getFund(), "en"),
                          null,
                          50.0
                      )
                  )
                  .build()
          )
          .build()

}
