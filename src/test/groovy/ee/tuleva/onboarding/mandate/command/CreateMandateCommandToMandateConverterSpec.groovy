package ee.tuleva.onboarding.mandate.command

import spock.lang.Specification

class CreateMandateCommandToMandateConverterSpec extends Specification {

    CreateMandateCommandToMandateConverter converter = new CreateMandateCommandToMandateConverter()


    def "converts to mandate"() {
        given:
        def command = new CreateMandateCommand()
        command.setPillar(3)
        command.setFutureContributionFundIsin("test")
        command.fundTransferExchanges = []
        when:
        def mandate = converter.convert(command)
        then:
        mandate.pillar == command.pillar
        mandate.futureContributionFundIsin.get() == command.futureContributionFundIsin
        mandate.id == null
    }

    def "converts to mandate, defaults to second pillar"() {
        given:
        def command = new CreateMandateCommand()
        command.setFutureContributionFundIsin("test")
        command.fundTransferExchanges = []
        when:
        def mandate = converter.convert(command)
        then:
        mandate.pillar == 2
        mandate.futureContributionFundIsin.get() == command.futureContributionFundIsin
        mandate.id == null
    }
}
