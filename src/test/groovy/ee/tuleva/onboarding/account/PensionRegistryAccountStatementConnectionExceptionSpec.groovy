package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.epis.account.FundBalanceDto
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class PensionRegistryAccountStatementConnectionExceptionSpec extends Specification {
    def "PensionRegistryAccountStatementConnectionException is using proper error code"() {
        given:
            def accountStatementConnectionException = new PensionRegistryAccountStatementConnectionException()
        expect:
            accountStatementConnectionException.errorsResponse.errors[0].code == "pension.registry.connection.exception"
    }
}
