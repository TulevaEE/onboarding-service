package ee.tuleva.onboarding.account


import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.account.FundBalanceDto
import spock.lang.Specification

import static ee.tuleva.onboarding.account.AccountStatementFixture.sampleConvertedFundBalanceWithActiveTulevaFund
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class AccountStatementServiceSpec extends Specification {

    def episService = Mock(EpisService)
    def fundBalanceConverter = Mock(FundBalanceDtoToFundBalanceConverter)

    def service = new AccountStatementService(episService, fundBalanceConverter)

    def "returns an account statement"() {
        given:
        def person = samplePerson()
        def fundBalanceDto = FundBalanceDto.builder().isin("someIsin").build()
        def fundBalance = sampleConvertedFundBalanceWithActiveTulevaFund.first()

        episService.getAccountStatement(person) >> [fundBalanceDto]
        fundBalanceConverter.convert(fundBalanceDto, person) >> fundBalance

        when:
        List<FundBalance> accountStatement = service.getAccountStatement(person)

        then:
        accountStatement == [fundBalance]
    }

    def "fundBalanceDto with no Isin code are filtered out and will not try to convert"() {
        given:
            def person = samplePerson()
            def fundBalanceDto = FundBalanceDto.builder().isin(null).build()
            episService.getAccountStatement(person) >> [fundBalanceDto]

        when:
            List<FundBalance> accountStatement = service.getAccountStatement(person)

        then:
            accountStatement.isEmpty()
            0 * fundBalanceConverter.convert(fundBalanceDto, person)
    }

    def "handles fundBalanceDto to fundBalance conversion exceptions"() {
        given:
        def person = samplePerson()
        def fundBalanceDto = FundBalanceDto.builder().isin("someIsin").build()

        episService.getAccountStatement(person) >> [fundBalanceDto]
        fundBalanceConverter.convert(fundBalanceDto, person) >> {
            throw new IllegalArgumentException()
        }

        when:
        service.getAccountStatement(person)

        then:
        thrown(IllegalStateException)
    }
}
