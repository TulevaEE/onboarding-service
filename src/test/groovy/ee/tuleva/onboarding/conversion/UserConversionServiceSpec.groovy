package ee.tuleva.onboarding.conversion

import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import static ee.tuleva.onboarding.conversion.ConversionHoldingFixture.toConversionHoldings
import static ee.tuleva.onboarding.account.AccountStatementFixture.*
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.fund.FundFixture.*
import static ee.tuleva.onboarding.pillar.Pillar.SECOND
import static ee.tuleva.onboarding.pillar.Pillar.THIRD

class UserConversionServiceSpec extends Specification {

  def conversionHoldings = Mock(ConversionHoldings)
  def conversionCashFlows = Mock(ConversionCashFlows)
  def pendingMandateApplications = Mock(PendingMandateApplications)
  def clock = Clock.fixed(Instant.parse("2019-12-30T10:06:01Z"), ZoneOffset.UTC)

  def service = new UserConversionService(conversionHoldings, conversionCashFlows,
      clock, pendingMandateApplications)

  def "GetConversion: Get conversion response for 2nd pillar withdrawal"() {
    given:
    conversionHoldings.forPerson(samplePerson) >> []
    pendingMandateApplications.getPendingExchanges(_, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []
    pendingMandateApplications.hasPendingWithdrawals(samplePerson, SECOND) >> true
    pendingMandateApplications.hasPendingWithdrawals(samplePerson, THIRD) >> false

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    response.secondPillar.pendingWithdrawal
    !response.thirdPillar.pendingWithdrawal
  }

  def "GetConversion: Get conversion response for 2nd pillar selection and transfer"() {
    given:
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(_, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(_, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(_, samplePerson) >> []

    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(_, samplePerson) >> []

    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(SECOND, samplePerson) >> [fullPending2ndPillarExchange]
    pendingMandateApplications.getPendingExchanges(THIRD, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(SECOND, samplePerson) >> [partialPending2ndPillarExchange]
    pendingMandateApplications.getPendingExchanges(THIRD, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(SECOND, samplePerson) >> [partialPending2ndPillarFromOwnToOwnExchange]
    pendingMandateApplications.getPendingExchanges(THIRD, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(SECOND, samplePerson) >> [partialPending2ndPillarExchange]
    pendingMandateApplications.getPendingExchanges(THIRD, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(SECOND, samplePerson) >> [fullPendingPikExchange]
    pendingMandateApplications.getPendingExchanges(THIRD, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(THIRD, samplePerson) >> [fullPending3rdPillarExchange]
    pendingMandateApplications.getPendingExchanges(SECOND, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(THIRD, samplePerson) >> [partialPending3rdPillarExchange]
    pendingMandateApplications.getPendingExchanges(SECOND, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(THIRD, samplePerson) >> [partialPending3rdPillarExchange]
    pendingMandateApplications.getPendingExchanges(SECOND, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(THIRD, samplePerson) >> [fullPending3rdPillarExitExchange]
    pendingMandateApplications.getPendingExchanges(SECOND, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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
    1 * conversionHoldings.forPerson(samplePerson) >> []
    pendingMandateApplications.getPendingExchanges(_, samplePerson) >> []

    conversionCashFlows.forPerson(samplePerson) >> [
        new ConversionCashFlow(2, 100.0, Instant.parse("2018-12-31T00:00:00+02:00"), true, true, false),
        new ConversionCashFlow(2, 1.0, Instant.parse("2019-01-01T00:00:00+02:00"), true, true, false),
        new ConversionCashFlow(2, 1.0, Instant.parse("2019-11-20T00:00:00+02:00"), true, true, false),
        new ConversionCashFlow(2, 1.0, Instant.parse("2019-12-20T00:00:00+02:00"), false, false, true),
        new ConversionCashFlow(2, 1.0, Instant.parse("2019-12-21T00:00:00+02:00"), false, false, true),

        new ConversionCashFlow(3, 100.0, Instant.parse("2018-12-31T00:00:00+02:00"), true, true, false),
        new ConversionCashFlow(3, 1.0, Instant.parse("2019-01-01T00:00:00+02:00"), true, true, false),
        new ConversionCashFlow(3, 1.0, Instant.parse("2019-01-02T00:00:00+02:00"), true, true, false),
        new ConversionCashFlow(3, 1.0, Instant.parse("2019-11-20T00:00:00+02:00"), true, true, false),
        new ConversionCashFlow(3, 20.0, Instant.parse("2019-12-20T00:00:00+02:00"), false, true, false),
        new ConversionCashFlow(3, 1.0, Instant.parse("2019-12-20T00:00:00+02:00"), false, false, true),
        new ConversionCashFlow(3, 1.0, Instant.parse("2019-12-21T00:00:00+02:00"), false, false, true),
    ]

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
    1 * conversionHoldings.forPerson(samplePerson) >> toConversionHoldings(accountBalanceResponse)
    pendingMandateApplications.getPendingExchanges(_, samplePerson) >> []
    conversionCashFlows.forPerson(samplePerson) >> []

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

  PendingExchange fullPending2ndPillarExchange = new PendingExchangeFixture(
      pillar: 2, fromOwnFund: false, toOwnFund: true, amount: 1.0,
      sourceIsin: lhv2ndPillarFund().isin, targetIsin: tuleva2ndPillarStockFund().isin,
      sourceFundFees: lhv2ndPillarFund().ongoingChargesFigure, targetFundFees: tuleva2ndPillarStockFund().ongoingChargesFigure)

  PendingExchange partialPending2ndPillarExchange = new PendingExchangeFixture(
      pillar: 2, fromOwnFund: false, toOwnFund: true, amount: 0.5,
      sourceIsin: lhv2ndPillarFund().isin, targetIsin: tuleva2ndPillarStockFund().isin,
      sourceFundFees: lhv2ndPillarFund().ongoingChargesFigure, targetFundFees: tuleva2ndPillarStockFund().ongoingChargesFigure)

  PendingExchange fullPendingPikExchange = new PendingExchangeFixture(
      pillar: 2, fromOwnFund: true, toOwnFund: false, amount: 1.0, toPik: true,
      sourceIsin: tuleva2ndPillarStockFund().isin,
      sourceFundFees: tuleva2ndPillarStockFund().ongoingChargesFigure)

  PendingExchange fullPending3rdPillarExchange = new PendingExchangeFixture(
      pillar: 3, fromOwnFund: false, toOwnFund: true, amount: 2343.8579,
      sourceIsin: lhv3rdPillarFund().isin, targetIsin: tuleva3rdPillarFund().isin,
      sourceFundFees: lhv3rdPillarFund().ongoingChargesFigure, targetFundFees: tuleva3rdPillarFund().ongoingChargesFigure)

  PendingExchange fullPending3rdPillarExitExchange = new PendingExchangeFixture(
      pillar: 3, fromOwnFund: true, toOwnFund: false, amount: 234.56,
      sourceIsin: tuleva3rdPillarFund().isin, targetIsin: lhv3rdPillarFund().isin,
      sourceFundFees: tuleva3rdPillarFund().ongoingChargesFigure, targetFundFees: lhv3rdPillarFund().ongoingChargesFigure)

  PendingExchange partialPending3rdPillarExchange = new PendingExchangeFixture(
      pillar: 3, fromOwnFund: false, toOwnFund: true, amount: 50.0,
      sourceIsin: lhv3rdPillarFund().isin, targetIsin: tuleva3rdPillarFund().isin,
      sourceFundFees: lhv3rdPillarFund().ongoingChargesFigure, targetFundFees: tuleva3rdPillarFund().ongoingChargesFigure)

  PendingExchange partialPending2ndPillarFromOwnToOwnExchange = new PendingExchangeFixture(
      pillar: 2, fromOwnFund: true, toOwnFund: true, amount: 0.01,
      sourceIsin: tuleva2ndPillarStockFund().isin, targetIsin: tuleva2ndPillarBondFund().isin,
      sourceFundFees: tuleva2ndPillarStockFund().ongoingChargesFigure, targetFundFees: tuleva2ndPillarBondFund().ongoingChargesFigure)

  def "payment is not complete when no recent cash contribution sums above zero"() {
    given:
    1 * conversionHoldings.forPerson(samplePerson) >> []
    pendingMandateApplications.getPendingExchanges(_, samplePerson) >> []

    conversionCashFlows.forPerson(samplePerson) >> [
        new ConversionCashFlow(3, 500.0, Instant.parse("2018-06-01T00:00:00+02:00"), true, true, false),
        new ConversionCashFlow(3, 100.0, Instant.parse("2019-12-01T00:00:00+02:00"), false, true, false),
        new ConversionCashFlow(3, 0.0, Instant.parse("2019-12-20T00:00:00+02:00"), true, true, false),
    ]

    when:
    ConversionResponse response = service.getConversion(samplePerson)

    then:
    !response.thirdPillar.paymentComplete
  }
}
