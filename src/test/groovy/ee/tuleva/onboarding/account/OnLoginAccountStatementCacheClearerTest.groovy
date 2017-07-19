package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.PersonFixture
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

class OnLoginAccountStatementCacheClearerTest extends Specification {

    AccountStatementService accountStatementService = Mock(AccountStatementService)
    OnLoginAccountStatementCacheClearer service =
            new OnLoginAccountStatementCacheClearer(accountStatementService)

    def "OnBeforeTokenGrantedEvent: Starts clearing cache on event"() {
        given:

        Person samplePerson = PersonFixture.samplePerson()

        OAuth2Authentication oAuth2Authentication = Mock({
            getPrincipal() >> samplePerson
        })

        BeforeTokenGrantedEvent beforeTokenGrantedEvent = new BeforeTokenGrantedEvent(this, oAuth2Authentication)

        when:
        service.onBeforeTokenGrantedEvent(beforeTokenGrantedEvent)

        then:
        1 * accountStatementService.clearCache(samplePerson)
    }
}
