package ee.tuleva.onboarding.mandate.builder

import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.account.FundBalance
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import ee.tuleva.onboarding.mandate.command.CreateMandateCommandWrapper
import ee.tuleva.onboarding.mandate.command.MandateFundTransferExchangeCommand
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture

class CreateMandateCommandToMandateConverterSpec extends Specification {

  AccountStatementService accountStatementService = Mock()
  FundRepository fundRepository = Mock()
  ConversionDecorator conversionDecorator = new ConversionDecorator()
  SecondPillarPaymentRateService secondPillarPaymentRateService = Mock()
  CreateMandateCommandToMandateConverter converter =
      new CreateMandateCommandToMandateConverter(accountStatementService, fundRepository, conversionDecorator, secondPillarPaymentRateService)

  def setup() {
    secondPillarPaymentRateService.getPaymentRates(_) >> new PaymentRates(4, null)
  }

  def "converts to mandate"() {
    given:
    def command = new CreateMandateCommand()
    command.setFutureContributionFundIsin("test")
    command.fundTransferExchanges = []
    command.address = addressFixture().build()
    def user = sampleUser().build()
    def person = authenticatedPersonFromUser(user).build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    when:
    def mandate = converter.convert(new CreateMandateCommandWrapper(command, person, user, conversion, contactDetails))
    then:
    mandate.pillar == 2
    mandate.user == user
    mandate.address == command.address
    mandate.futureContributionFundIsin.get() == command.futureContributionFundIsin
    mandate.id == null
    mandate.metadata == [
        isSecondPillarActive            : contactDetails.secondPillarActive,
        isSecondPillarFullyConverted    : conversion.secondPillarFullyConverted,
        isThirdPillarActive             : contactDetails.thirdPillarActive,
        isThirdPillarFullyConverted     : conversion.thirdPillarFullyConverted,
        isSecondPillarPartiallyConverted: conversion.secondPillarPartiallyConverted,
        isThirdPillarPartiallyConverted : conversion.thirdPillarPartiallyConverted,
        secondPillarWeightedAverageFee  : conversion.secondPillarWeightedAverageFee,
        thirdPillarWeightedAverageFee   : conversion.thirdPillarWeightedAverageFee,
        secondPillarPaymentRate         : 4,
        authAttributes                  : [:]
    ]
    0 * accountStatementService.getAccountStatement(_)
    1 * fundRepository.findByIsin("test") >> Fund.builder().pillar(2).isin("test").build()
  }

  def "converts to mandate, calculates units for third pillar"() {
    given:
    def sourceIsin = 'AA1234567'
    def targetIsin = 'AA1234568'
    def command = new CreateMandateCommand()
    command.setFutureContributionFundIsin(sourceIsin)
    def fundTransfer = new MandateFundTransferExchangeCommand()
    fundTransfer.amount = 0.5
    fundTransfer.sourceFundIsin = sourceIsin
    fundTransfer.targetFundIsin = targetIsin
    command.fundTransferExchanges = [fundTransfer]
    def fund = Fund.builder().pillar(3).isin(sourceIsin).build()
    def user = sampleUser().build()
    def person = authenticatedPersonFromUser(user).build()
    def fundBalance = FundBalance.builder()
        .units(500.1234)
        .fund(fund)
        .build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    when:
    def mandate = converter.convert(new CreateMandateCommandWrapper(command, person, user, conversion, contactDetails))
    then:
    mandate.pillar == 3
    mandate.user == user
    mandate.address == command.address
    mandate.futureContributionFundIsin.get() == command.futureContributionFundIsin
    !mandate.fundTransferExchanges.isEmpty()
    mandate.fundTransferExchanges[0].sourceFundIsin == sourceIsin
    mandate.fundTransferExchanges[0].targetFundIsin == targetIsin
    mandate.fundTransferExchanges[0].amount == 250.0617
    mandate.id == null
    mandate.metadata == [
        isSecondPillarActive            : contactDetails.secondPillarActive,
        isSecondPillarFullyConverted    : conversion.secondPillarFullyConverted,
        isThirdPillarActive             : contactDetails.thirdPillarActive,
        isThirdPillarFullyConverted     : conversion.thirdPillarFullyConverted,
        isSecondPillarPartiallyConverted: conversion.secondPillarPartiallyConverted,
        isThirdPillarPartiallyConverted : conversion.thirdPillarPartiallyConverted,
        secondPillarWeightedAverageFee  : conversion.secondPillarWeightedAverageFee,
        thirdPillarWeightedAverageFee   : conversion.thirdPillarWeightedAverageFee,
        secondPillarPaymentRate         : 4,
        authAttributes                  : [:]
    ]
    1 * accountStatementService.getAccountStatement(user) >> [fundBalance]
    1 * fundRepository.findByIsin(sourceIsin) >> fund
  }
}
