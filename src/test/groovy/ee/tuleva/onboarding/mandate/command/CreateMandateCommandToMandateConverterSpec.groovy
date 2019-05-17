package ee.tuleva.onboarding.mandate.command

import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.account.FundBalance
import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.fund.Fund
import spock.lang.Specification

class CreateMandateCommandToMandateConverterSpec extends Specification {

    AccountStatementService accountStatementService = Mock()
    CreateMandateCommandToMandateConverter converter = new CreateMandateCommandToMandateConverter(accountStatementService)


    def "converts to mandate"() {
        given:
        def command = new CreateMandateCommand()
        command.setPillar(3)
        command.setFutureContributionFundIsin("test")
        command.fundTransferExchanges = []
        def user = UserFixture.sampleUser().build()
        when:
        def mandate = converter.convert(new CreateMandateCommandWithUser(command, user))
        then:
        mandate.pillar == command.pillar
        mandate.user == user
        mandate.futureContributionFundIsin.get() == command.futureContributionFundIsin
        mandate.id == null
        0 * accountStatementService.getAccountStatement(_)
    }

    def "converts to mandate, defaults to second pillar"() {
        given:
        def command = new CreateMandateCommand()
        command.setFutureContributionFundIsin("test")
        command.fundTransferExchanges = []
        def user = UserFixture.sampleUser().build()
        when:
        def mandate = converter.convert(new CreateMandateCommandWithUser(command, user))
        then:
        mandate.pillar == 2
        mandate.user == user
        mandate.futureContributionFundIsin.get() == command.futureContributionFundIsin
        mandate.id == null
        0 * accountStatementService.getAccountStatement(_)
    }

    def "converts to mandate, calculates units for third pillar"() {
        given:
        def sourceIsin = 'AA1234567'
        def targetIsin = 'AA1234568'
        def command = new CreateMandateCommand()
        command.setPillar(3)
        command.setFutureContributionFundIsin("test")
        def fundTransfer = new MandateFundTransferExchangeCommand()
        fundTransfer.amount = 0.5
        fundTransfer.sourceFundIsin = sourceIsin
        fundTransfer.targetFundIsin = targetIsin
        command.fundTransferExchanges = [fundTransfer]
        def user = UserFixture.sampleUser().build()
        def fundBalance = FundBalance.builder()
            .pillar(3)
            .units(500.0)
            .fund(Fund.builder().pillar(3).isin(sourceIsin).build())
            .build()
        when:
        def mandate = converter.convert(new CreateMandateCommandWithUser(command, user))
        then:
        mandate.pillar == 3
        mandate.user == user
        mandate.futureContributionFundIsin.get() == command.futureContributionFundIsin
        !mandate.fundTransferExchanges.isEmpty()
        mandate.fundTransferExchanges[0].sourceFundIsin == sourceIsin
        mandate.fundTransferExchanges[0].targetFundIsin == targetIsin
        mandate.fundTransferExchanges[0].amount == 250.0
        mandate.id == null
        1 * accountStatementService.getAccountStatement(user) >> [fundBalance]
    }
}
