package ee.tuleva.onboarding.conversion

import ee.tuleva.onboarding.account.AccountStatementFixture
import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.auth.PersonFixture
import spock.lang.Specification

class UserConversionServiceSpec extends Specification {

    AccountStatementService accountStatementService = Mock(AccountStatementService)

    UserConversionService service = new UserConversionService(accountStatementService)

    def "GetConversion: Get conversion response for fund selection"() {
        given:
        1 * accountStatementService.getMyPensionAccountStatement(
                PersonFixture.samplePerson,
                _ as UUID
        ) >> accountBalanceResponse

        when:
        ConversionResponse conversionResponse = service.getConversion(
                PersonFixture.samplePerson
        )
        then:
        conversionResponse.selectionComplete == selectionComplete

        where:
        accountBalanceResponse                                                       | selectionComplete
        AccountStatementFixture.sampleConvertedFundBalanceWithActiveTulevaFund       | true
        AccountStatementFixture.sampleNonConvertedFundBalanceWithActiveNonTulevaFund | false

    }

    def "GetConversion: Get conversion response for fund transfer"() {
        given:
        1 * accountStatementService.getMyPensionAccountStatement(
                PersonFixture.samplePerson,
                _ as UUID
        ) >> accountBalanceResponse

        when:
        ConversionResponse conversionResponse = service.getConversion(
                PersonFixture.samplePerson
        )
        then:
        conversionResponse.transfersComplete == transferComplete

        where:
        accountBalanceResponse                                                       | transferComplete
        AccountStatementFixture.sampleConvertedFundBalanceWithActiveTulevaFund       | true
        AccountStatementFixture.sampleNonConvertedFundBalanceWithActiveNonTulevaFund | false

    }

}
