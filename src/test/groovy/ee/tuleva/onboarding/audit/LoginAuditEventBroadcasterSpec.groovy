package ee.tuleva.onboarding.audit

import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.principal.Person
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

class LoginAuditEventBroadcasterSpec extends Specification {

    AuditEventPublisher auditEventPublisher = Mock(AuditEventPublisher)
    LoginAuditEventBroadcaster service = new LoginAuditEventBroadcaster(auditEventPublisher)

    def "OnBeforeTokenGrantedEvent: Broadcast login event"() {
        given:
        String samplePersonalCode = "personalCode"

        OAuth2Authentication sampleOAuth2Authentication = Mock(OAuth2Authentication, {
            getPrincipal() >> Mock(Person,{
                getPersonalCode() >> samplePersonalCode
            })
        })

        BeforeTokenGrantedEvent beforeTokenGrantedEvent = Mock(BeforeTokenGrantedEvent, {
            getAuthentication() >> sampleOAuth2Authentication
        })

        when:
        service.onBeforeTokenGrantedEvent(beforeTokenGrantedEvent)
        then:
        1 * auditEventPublisher.publish(samplePersonalCode, AuditEventType.LOGIN)

    }
}
