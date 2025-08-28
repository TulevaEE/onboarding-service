package ee.tuleva.onboarding.mandate.cancellation

import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.FundTransferExchange
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateType
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleEarlyWithdrawalApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleTransferApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleWithdrawalApplicationDto

class CancellationMandateBuilderSpec extends Specification {

  ConversionDecorator conversionDecorator = new ConversionDecorator()
  FundRepository fundRepository = Mock()
  SecondPillarPaymentRateService secondPillarPaymentRateService = Mock()
  CancellationMandateBuilder cancellationMandateBuilder

  def setup() {
    secondPillarPaymentRateService.getPaymentRates(_) >> new PaymentRates(4, null)
    cancellationMandateBuilder = new CancellationMandateBuilder(conversionDecorator, fundRepository, secondPillarPaymentRateService)
  }

  def "can build withdrawal cancellation mandates"() {
    given:
    def applicationToCancel = sampleWithdrawalApplicationDto()
    def user = sampleUser().build()
    def person = authenticatedPersonFromUser(user).build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()

    when:
    Mandate mandate = cancellationMandateBuilder.build(applicationToCancel, person, user, conversion, contactDetails)

    then:
    mandate.pillar == 2
    mandate.user == user
    mandate.address == contactDetails.address
    mandate.futureContributionFundIsin == Optional.empty()
    mandate.fundTransferExchanges == null
    mandate.getGenericMandateDto().getMandateType() == MandateType.WITHDRAWAL_CANCELLATION
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
  }

  def "can build early withdrawal cancellation mandates"() {
    given:
    def applicationToCancel = sampleEarlyWithdrawalApplicationDto()
    def user = sampleUser().build()
    def person = authenticatedPersonFromUser(user).build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()

    when:
    Mandate mandate = cancellationMandateBuilder.build(applicationToCancel, person, user, conversion, contactDetails)

    then:
    mandate.pillar == 2
    mandate.user == user
    mandate.address == contactDetails.address
    mandate.futureContributionFundIsin == Optional.empty()
    mandate.fundTransferExchanges == null
    mandate.getGenericMandateDto().getMandateType() == MandateType.EARLY_WITHDRAWAL_CANCELLATION
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
  }

  def "can build 2nd pillar transfer cancellation mandates"() {
    given:
    def applicationToCancel = sampleTransferApplicationDto()
    def user = sampleUser().build()
    def person = authenticatedPersonFromUser(user).build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    def fundTransferExchange = FundTransferExchange.builder().sourceFundIsin("source")
    fundRepository.findByIsin("source") >> Fund.builder().isin("source").pillar(2).build()

    when:
    Mandate mandate = cancellationMandateBuilder.build(applicationToCancel, person, user, conversion, contactDetails)

    then:
    mandate.pillar == 2
    mandate.user == user
    mandate.address == contactDetails.address
    mandate.futureContributionFundIsin == Optional.empty()
    mandate.fundTransferExchanges == [fundTransferExchange.mandate(mandate).build()]
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
  }

  def "can build 3rd pillar transfer cancellation mandates"() {
    given:
    def applicationToCancel = sampleTransferApplicationDto()
    def user = sampleUser().build()
    def person = authenticatedPersonFromUser(user).build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    def fundTransferExchange = FundTransferExchange.builder().sourceFundIsin("source")
    fundRepository.findByIsin("source") >> Fund.builder().isin("source").pillar(3).build()

    when:
    Mandate mandate = cancellationMandateBuilder.build(applicationToCancel, person, user, conversion, contactDetails)

    then:
    mandate.pillar == 3
    mandate.user == user
    mandate.address == contactDetails.address
    mandate.futureContributionFundIsin == Optional.empty()
    mandate.fundTransferExchanges == [fundTransferExchange.mandate(mandate).build()]
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
  }
}
