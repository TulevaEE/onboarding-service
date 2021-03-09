package ee.tuleva.onboarding.mandate.cancellation


import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture

class CancellationMandateBuilderSpec extends Specification {

    ConversionDecorator conversionDecorator = new ConversionDecorator()
    CancellationMandateBuilder cancellationMandateBuilder

    def setup() {
        cancellationMandateBuilder = new CancellationMandateBuilder(conversionDecorator)
    }

    def "can build cancellation mandates"() {
        given:
        def applicationTypeToCancel = WITHDRAWAL
        def user = sampleUser().build()
        def conversion = fullyConverted()
        def contactDetails = contactDetailsFixture()

        when:
        Mandate mandate = cancellationMandateBuilder.build(applicationTypeToCancel, user, conversion, contactDetails)

        then:
        mandate.pillar == 2
        mandate.user == user
        mandate.address == contactDetails.address
        mandate.futureContributionFundIsin == Optional.empty()
        mandate.fundTransferExchanges == null
        mandate.metadata == [
            applicationTypeToCancel: applicationTypeToCancel,
            isSecondPillarActive: contactDetails.isSecondPillarActive(),
            isSecondPillarFullyConverted: conversion.isSecondPillarFullyConverted(),
            isThirdPillarActive: contactDetails.isThirdPillarActive(),
            isThirdPillarFullyConverted: conversion.isThirdPillarFullyConverted()
        ]
    }
}
