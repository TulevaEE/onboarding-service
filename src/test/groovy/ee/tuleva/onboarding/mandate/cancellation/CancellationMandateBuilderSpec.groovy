package ee.tuleva.onboarding.mandate.cancellation

import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.FundTransferExchange
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleTransferApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleWithdrawalApplicationDto

class CancellationMandateBuilderSpec extends Specification {

  ConversionDecorator conversionDecorator = new ConversionDecorator()
  FundRepository fundRepository = Mock()
  CancellationMandateBuilder cancellationMandateBuilder

  def setup() {
    cancellationMandateBuilder = new CancellationMandateBuilder(conversionDecorator, fundRepository)
  }

  def "can build withdrawal cancellation mandates"() {
    given:
    def applicationToCancel = sampleWithdrawalApplicationDto()
    def user = sampleUser().build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()

    when:
    Mandate mandate = cancellationMandateBuilder.build(applicationToCancel, user, conversion, contactDetails)

    then:
    mandate.pillar == 2
    mandate.user == user
    mandate.address == contactDetails.address
    mandate.futureContributionFundIsin == Optional.empty()
    mandate.fundTransferExchanges == null
    mandate.metadata == [
      applicationTypeToCancel     : applicationToCancel.getType(),
      isSecondPillarActive        : contactDetails.isSecondPillarActive(),
      isSecondPillarFullyConverted: conversion.isSecondPillarFullyConverted(),
      isThirdPillarActive         : contactDetails.isThirdPillarActive(),
      isThirdPillarFullyConverted : conversion.isThirdPillarFullyConverted()
    ]
  }

  def "can build 2nd pillar transfer cancellation mandates"() {
    given:
    def applicationToCancel = sampleTransferApplicationDto()
    def user = sampleUser().build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    def fundTransferExchange = FundTransferExchange.builder().sourceFundIsin("source")
    fundRepository.findByIsin("source") >> Fund.builder().isin("source").pillar(2).build()

    when:
    Mandate mandate = cancellationMandateBuilder.build(applicationToCancel, user, conversion, contactDetails)

    then:
    mandate.pillar == 2
    mandate.user == user
    mandate.address == contactDetails.address
    mandate.futureContributionFundIsin == Optional.empty()
    mandate.fundTransferExchanges == [fundTransferExchange.mandate(mandate).build()]
    mandate.metadata == [
      isSecondPillarActive        : contactDetails.isSecondPillarActive(),
      isSecondPillarFullyConverted: conversion.isSecondPillarFullyConverted(),
      isThirdPillarActive         : contactDetails.isThirdPillarActive(),
      isThirdPillarFullyConverted : conversion.isThirdPillarFullyConverted()
    ]
  }

  def "can build 3rd pillar transfer cancellation mandates"() {
    given:
    def applicationToCancel = sampleTransferApplicationDto()
    def user = sampleUser().build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()
    def fundTransferExchange = FundTransferExchange.builder().sourceFundIsin("source")
    fundRepository.findByIsin("source") >> Fund.builder().isin("source").pillar(3).build()

    when:
    Mandate mandate = cancellationMandateBuilder.build(applicationToCancel, user, conversion, contactDetails)

    then:
    mandate.pillar == 3
    mandate.user == user
    mandate.address == contactDetails.address
    mandate.futureContributionFundIsin == Optional.empty()
    mandate.fundTransferExchanges == [fundTransferExchange.mandate(mandate).build()]
    mandate.metadata == [
      isSecondPillarActive        : contactDetails.isSecondPillarActive(),
      isSecondPillarFullyConverted: conversion.isSecondPillarFullyConverted(),
      isThirdPillarActive         : contactDetails.isThirdPillarActive(),
      isThirdPillarFullyConverted : conversion.isThirdPillarFullyConverted()
    ]
  }
}
