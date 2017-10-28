package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.account.FundBalanceDto
import spock.lang.Specification

import static ee.tuleva.onboarding.account.AccountStatementFixture.*
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
        fundBalanceConverter.convert(fundBalanceDto) >> fundBalance

        when:
        List<FundBalance> accountStatement = service.getAccountStatement(person)

        then:
        accountStatement == [fundBalance]
    }
}
