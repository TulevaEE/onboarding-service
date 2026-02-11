package ee.tuleva.onboarding.conversion

import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.account.CashFlowService
import ee.tuleva.onboarding.epis.cashflows.CashFlow
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement
import ee.tuleva.onboarding.fund.ApiFundResponse
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.application.Application
import ee.tuleva.onboarding.mandate.application.ApplicationService
import ee.tuleva.onboarding.mandate.application.Exchange
import ee.tuleva.onboarding.mandate.application.TransferApplicationDetails
import ee.tuleva.onboarding.pillar.Pillar
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import static ee.tuleva.onboarding.account.AccountStatementFixture.*
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.*
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.fund.FundFixture.*
import static ee.tuleva.onboarding.pillar.Pillar.SECOND
import static ee.tuleva.onboarding.pillar.Pillar.THIRD

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
    applicationService.hasPendingWithdrawals(samplePerson, SECOND) >> true
    applicationService.hasPendingWithdrawals(samplePerson, THIRD) >> false

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
    accountBalanceResponse               | secondPillarSelectionComplete | secondPillarTransfersComplete
    activeTuleva2ndPillarFundBalance     | true                          | true
    activeExternal2ndPillarFundBalance   | false                         | false
    inactiveTuleva2ndPillarFundBalance   | false                         | true
    inactiveExternal2ndPillarFundBalance | true                          | false
    []                                   | false                         | true
  }

  def "get partial conversion info for 2nd pillar"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> []
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.secondPillar.selectionPartial == secondPillarSelectionPartial
    response.secondPillar.transfersPartial == secondPillarTransfersPartial
    !response.secondPillar.pendingWithdrawal

    where:
    accountBalanceResponse               | secondPillarSelectionPartial | secondPillarTransfersPartial
    activeTuleva2ndPillarFundBalance     | true                         | true
    activeExternal2ndPillarFundBalance   | false                        | true
    inactiveTuleva2ndPillarFundBalance   | false                        | true
    inactiveExternal2ndPillarFundBalance | true                         | true
    fullyExternal2ndPillarFundBalance    | false                        | false
    onlyActiveTuleva2ndPillarFundBalance | true                         | false
    []                                   | false                        | true
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
    accountBalanceResponse               | thirdPillarSelectionComplete | thirdPillarTransfersComplete
    activeTuleva3rdPillarFundBalance     | true                         | true
    activeTuleva3rdPillarFund            | true                         | true
    activeExternal3rdPillarFundBalance   | false                        | false
    inactiveTuleva3rdPillarFundBalance   | false                        | true
    inactiveExternal3rdPillarFundBalance | true                         | false
    fullyExternal3rdPillarFundBalance    | false                        | false
    onlyActiveTuleva3rdPillarFundBalance | true                         | false
    []                                   | false                        | true
  }

  def "GetConversion: Get partial conversion response for 3rd pillar selection and transfer"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> []

    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.thirdPillar.selectionPartial == thirdPillarSelectionPartial
    response.thirdPillar.transfersPartial == thirdPillarTransfersPartial

    where:
    accountBalanceResponse               | thirdPillarSelectionPartial | thirdPillarTransfersPartial
    activeTuleva3rdPillarFundBalance     | true                        | true
    activeTuleva3rdPillarFund            | true                        | true
    activeExternal3rdPillarFundBalance   | false                       | true
    inactiveTuleva3rdPillarFundBalance   | false                       | true
    inactiveExternal3rdPillarFundBalance | true                        | true
    fullyExternal3rdPillarFundBalance    | false                       | false
    onlyActiveTuleva3rdPillarFundBalance | true                        | false
    []                                   | false                       | true
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

  def "GetConversion 2nd pillar: works with pending transfers from own fund to own fund"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> [partialPending2ndPillarFromOwnToOwnApplication]
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.secondPillar.selectionComplete == secondPillarSelectionComplete
    response.secondPillar.transfersComplete == secondPillarTransfersComplete

    where:
    accountBalanceResponse             | secondPillarSelectionComplete | secondPillarTransfersComplete
    activeTuleva2ndPillarFundBalance   | true                          | true
  }

  def "get partial conversion for 2nd pillar given pending mandates cover the lack"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> [partialPending2ndPillarApplication]
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.secondPillar.selectionPartial == secondPillarSelectionPartial
    response.secondPillar.transfersPartial == secondPillarTransfersPartial

    where:
    accountBalanceResponse               | secondPillarSelectionPartial | secondPillarTransfersPartial
    inactiveExternal2ndPillarFundBalance | true                         | true
    fullyExternal2ndPillarFundBalance    | false                        | true
    []                                   | false                        | true
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
    activeTuleva2ndPillarFundBalance   | true                          | false
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
    accountBalanceResponse              | thirdPillarSelectionComplete | thirdPillarTransfersComplete
    activeTuleva3rdPillarFundBalance    | true                         | true
    activeExternal3rdPillarFundBalance  | false                        | true
    pendingExternal3rdPillarFundBalance | true                         | true
  }

  def "get partial conversion for 3rd pillar given pending mandates cover the lack"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> [partialPending3rdPillarApplication]
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.thirdPillar.selectionPartial == thirdPillarSelectionPartial
    response.thirdPillar.transfersPartial == thirdPillarTransfersPartial

    where:
    accountBalanceResponse               | thirdPillarSelectionPartial | thirdPillarTransfersPartial
    inactiveExternal3rdPillarFundBalance | true                        | true
    fullyExternal3rdPillarFundBalance    | false                       | true
    []                                   | false                       | true
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

  def "GetConversion: Get conversion response for 3rd pillar pending exit transfer"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> [fullPending3rdPillarExit]
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.thirdPillar.selectionComplete == thirdPillarSelectionComplete
    response.thirdPillar.transfersComplete == thirdPillarTransfersComplete
    response.thirdPillar.selectionPartial == thirdPillarSelectionPartial
    response.thirdPillar.transfersPartial == thirdPillarTransfersPartial

    where:
    accountBalanceResponse             | thirdPillarSelectionComplete | thirdPillarTransfersComplete | thirdPillarSelectionPartial | thirdPillarTransfersPartial
    activeTuleva3rdPillarFundBalance   | true                         | false                        | true                        | false
    activeExternal3rdPillarFundBalance | false                        | false                        | false                       | false
  }

  def "calculates contribution and subtraction sums"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> []
    applicationService.getTransferApplications(PENDING, samplePerson) >> []
    def secondPillar = "EE123"
    def thirdPillar = "EE234"
    fundRepository.findByIsin(secondPillar) >> Fund.builder().pillar(2).build()
    fundRepository.findByIsin(thirdPillar) >> Fund.builder().pillar(3).build()

    cashFlowService.getCashFlowStatement(samplePerson) >> CashFlowStatement.builder()
        .transactions([
            CashFlow.builder().isin(secondPillar).time(Instant.parse("2018-12-31T00:00:00+02:00")).amount(100.0).currency(EUR).type(CONTRIBUTION_CASH).build(),
            CashFlow.builder().isin(secondPillar).time(Instant.parse("2019-01-01T00:00:00+02:00")).amount(1.0).currency(EUR).type(CONTRIBUTION_CASH).build(),
            CashFlow.builder().isin(secondPillar).time(Instant.parse("2019-11-20T00:00:00+02:00")).amount(1.0).currency(EUR).type(CONTRIBUTION_CASH).build(),
            CashFlow.builder().isin(secondPillar).time(Instant.parse("2019-12-20T00:00:00+02:00")).amount(1.0).currency(EUR).type(SUBTRACTION).build(),
            CashFlow.builder().isin(secondPillar).time(Instant.parse("2019-12-21T00:00:00+02:00")).amount(1.0).currency(EUR).type(SUBTRACTION).build(),

            CashFlow.builder().isin(thirdPillar).time(Instant.parse("2018-12-31T00:00:00+02:00")).amount(100.0).currency(EUR).type(CONTRIBUTION_CASH).build(),
            CashFlow.builder().isin(thirdPillar).time(Instant.parse("2019-01-01T00:00:00+02:00")).amount(1.0).currency(EUR).type(CONTRIBUTION_CASH).build(),
            CashFlow.builder().isin(thirdPillar).time(Instant.parse("2019-01-02T00:00:00+02:00")).amount(1.0).currency(EUR).type(CONTRIBUTION_CASH).build(),
            CashFlow.builder().isin(thirdPillar).time(Instant.parse("2019-11-20T00:00:00+02:00")).amount(1.0).currency(EUR).type(CONTRIBUTION_CASH).build(),
            CashFlow.builder().isin(thirdPillar).time(Instant.parse("2019-12-20T00:00:00+02:00")).amount(20.0).currency(EUR).type(CONTRIBUTION).build(),
            CashFlow.builder().isin(thirdPillar).time(Instant.parse("2019-12-20T00:00:00+02:00")).amount(1.0).currency(EUR).type(SUBTRACTION).build(),
            CashFlow.builder().isin(thirdPillar).time(Instant.parse("2019-12-21T00:00:00+02:00")).amount(1.0).currency(EUR).type(SUBTRACTION).build(),
        ])
        .build()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    with(response.secondPillar) {
      contribution.yearToDate == 1.0
      contribution.lastYear == 101.0
      contribution.total == 102.0
      subtraction.yearToDate == 2.0
      subtraction.lastYear == 0
      subtraction.total == 2.0
      paymentComplete == null
    }
    with(response.thirdPillar) {
      contribution.yearToDate == 2.0
      contribution.lastYear == 101.0
      contribution.total == 123.0
      subtraction.yearToDate == 2.0
      subtraction.lastYear == 0.0
      subtraction.total == 2.0
      paymentComplete
    }
  }

  def "calculates weighted average fees"() {
    given:
    1 * accountStatementService.getAccountStatement(samplePerson) >> accountBalanceResponse
    applicationService.getTransferApplications(PENDING, samplePerson) >> []
    cashFlowService.getCashFlowStatement(samplePerson) >> new CashFlowStatement()

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.secondPillar.weightedAverageFee == secondPillarWeightedAverageFee
    response.thirdPillar.weightedAverageFee == thirdPillarWeightedAverageFee
    response.weightedAverageFee == totalWeightedAverageFee

    where:
    accountBalanceResponse               | secondPillarWeightedAverageFee | thirdPillarWeightedAverageFee | totalWeightedAverageFee
    []                                   | 0.0                            | 0.0                           | 0.0

    activeTuleva2ndPillarFundBalance     | 0.005                          | 0.0                           | 0.005
    activeExternal2ndPillarFundBalance   | 0.0075                         | 0.0                           | 0.0075
    inactiveTuleva2ndPillarFundBalance   | 0.005                          | 0.0                           | 0.005
    inactiveExternal2ndPillarFundBalance | 0.0075                         | 0.0                           | 0.0075
    fullyExternal2ndPillarFundBalance    | 0.01                           | 0.0                           | 0.01
    onlyActiveTuleva2ndPillarFundBalance | 0.01                           | 0.0                           | 0.01

    activeTuleva3rdPillarFundBalance     | 0.0                            | 0.0057                        | 0.0057
    activeTuleva3rdPillarFund            | 0.0                            | 0.005                         | 0.005
    activeExternal3rdPillarFundBalance   | 0.0                            | 0.0075                        | 0.0075
    inactiveTuleva3rdPillarFundBalance   | 0.0                            | 0.005                         | 0.005
    inactiveExternal3rdPillarFundBalance | 0.0                            | 0.0075                        | 0.0075
    fullyExternal3rdPillarFundBalance    | 0.0                            | 0.01                          | 0.01
    onlyActiveTuleva3rdPillarFundBalance | 0.0                            | 0.01                          | 0.01
  }

  Application<TransferApplicationDetails> fullPending2ndPillarApplication =
      Application.<TransferApplicationDetails> builder()
          .status(PENDING)
          .details(
              TransferApplicationDetails.builder()
                  .sourceFund(new ApiFundResponse(lhv2ndPillarFund(), Locale.ENGLISH)
                  )
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(lhv2ndPillarFund(), Locale.ENGLISH),
                          new ApiFundResponse(tuleva2ndPillarStockFund(), Locale.ENGLISH),
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
                  .sourceFund(new ApiFundResponse(lhv2ndPillarFund(), Locale.ENGLISH)
                  )
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(lhv2ndPillarFund(), Locale.ENGLISH),
                          new ApiFundResponse(tuleva2ndPillarStockFund(), Locale.ENGLISH),
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
                  .sourceFund(new ApiFundResponse(tuleva2ndPillarStockFund(), Locale.ENGLISH)
                  )
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(tuleva2ndPillarStockFund(), Locale.ENGLISH),
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
                  .sourceFund(new ApiFundResponse(lhv3rdPillarFund(), Locale.ENGLISH))
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(lhv3rdPillarFund(), Locale.ENGLISH),
                          new ApiFundResponse(tuleva3rdPillarFund(), Locale.ENGLISH),
                          null,
                          2343.8579 // 100% of the FundBalance bookValue
                      )
                  )
                  .build()
          )
          .build()

  Application<TransferApplicationDetails> fullPending3rdPillarExit =
      Application.<TransferApplicationDetails> builder()
          .status(PENDING)
          .details(
              TransferApplicationDetails.builder()
                  .sourceFund(new ApiFundResponse(tuleva3rdPillarFund(), Locale.ENGLISH))
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(tuleva3rdPillarFund(), Locale.ENGLISH),
                          new ApiFundResponse(lhv3rdPillarFund(), Locale.ENGLISH),
                          null,
                          234.56 // 100% of the FundBalance bookValue
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
                  .sourceFund(new ApiFundResponse(lhv3rdPillarFund(), Locale.ENGLISH))
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(lhv3rdPillarFund(), Locale.ENGLISH),
                          new ApiFundResponse(tuleva3rdPillarFund(), Locale.ENGLISH),
                          null,
                          50.0
                      )
                  )
                  .build()
          )
          .build()


  Application<TransferApplicationDetails> partialPending2ndPillarFromOwnToOwnApplication =
      Application.<TransferApplicationDetails> builder()
          .status(PENDING)
          .details(
              TransferApplicationDetails.builder()
                  .sourceFund(new ApiFundResponse(tuleva2ndPillarStockFund(), Locale.ENGLISH)
                  )
                  .exchange(
                      new Exchange(
                          new ApiFundResponse(tuleva2ndPillarStockFund(), Locale.ENGLISH),
                          new ApiFundResponse(tuleva2ndPillarBondFund(), Locale.ENGLISH),
                          null,
                          0.01
                      )
                  )
                  .build()
          )
          .build()
}
