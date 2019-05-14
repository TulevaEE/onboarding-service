package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.EpisService
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class OnLoginAccountStatementCacheClearerSpec extends Specification {

    EpisService episService = Mock(EpisService)
    OnLoginAccountStatementCacheClearer service =
        new OnLoginAccountStatementCacheClearer(episService)

    def "OnBeforeTokenGrantedEvent: Starts clearing cache on event"() {
        given:

        Person samplePerson = samplePerson()

        OAuth2Authentication oAuth2Authentication = Mock({
            getPrincipal() >> samplePerson
        })

        BeforeTokenGrantedEvent beforeTokenGrantedEvent = new BeforeTokenGrantedEvent(this, oAuth2Authentication, GrantType.ID_CARD)

        when:
        service.onBeforeTokenGrantedEvent(beforeTokenGrantedEvent)

        then:
        1 * episService.clearCache(samplePerson)
    }
}
