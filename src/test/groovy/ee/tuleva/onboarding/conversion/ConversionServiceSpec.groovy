package ee.tuleva.onboarding.conversion

import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.auth.PersonFixture
import spock.lang.Specification

class ConversionServiceSpec extends Specification {

    AccountStatementService accountStatementService = Mock(AccountStatementService)

    ConversionService service = new ConversionService(accountStatementService)

    def "GetConversion: Get conversion response"() {

        UUID uuid = UUID.randomUUID()

        1 * accountStatementService.getMyPensionAccountStatement(
                PersonFixture.samplePerson,
                uuid
        ) >>

        when:
        ConversionResponse conversionResponse = service.getConversion()
        then:
        conversionResponse != null
    }
}
