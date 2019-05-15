package ee.tuleva.onboarding.audit

import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.idcard.IdCardSession
import ee.tuleva.onboarding.auth.idcard.IdDocumentType
import ee.tuleva.onboarding.auth.mobileid.MobileIdFixture
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.smartid.SmartIdFixture
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.GrantType.*

class LoginAuditEventBroadcasterSpec extends Specification {

    AuditEventPublisher auditEventPublisher = Mock(AuditEventPublisher)
    LoginAuditEventBroadcaster service = new LoginAuditEventBroadcaster(auditEventPublisher)

    def "OnBeforeTokenGrantedEvent: Broadcast login event"(GrantType grantType, String document, Object credentials) {
        given:
        String samplePersonalCode = "personalCode"

        OAuth2Authentication sampleOAuth2Authentication = Mock(OAuth2Authentication, {
            getPrincipal() >> Mock(Person, {
                getPersonalCode() >> samplePersonalCode
            })
            getUserAuthentication() >> Mock(Authentication, {
                getCredentials() >> credentials
            })
        })

        BeforeTokenGrantedEvent beforeTokenGrantedEvent = Mock(BeforeTokenGrantedEvent, {
            getAuthentication() >> sampleOAuth2Authentication
            getGrantType() >> grantType
        })

        when:
        service.onBeforeTokenGrantedEvent(beforeTokenGrantedEvent)
        then:
        if (document != null) {
            1 * auditEventPublisher.publish(samplePersonalCode, AuditEventType.LOGIN, "method=$grantType", "document=$document")
        } else {
            1 * auditEventPublisher.publish(samplePersonalCode, AuditEventType.LOGIN, "method=$grantType")
        }
        where:
        grantType | document                   | credentials
        ID_CARD   | "DIGITAL_ID_CARD"          | new IdCardSession("Chuck", "Norris", "38512121212", IdDocumentType.DIGITAL_ID_CARD)
        ID_CARD   | "OLD_ID_CARD"              | new IdCardSession("Chuck", "Norris", "38512121212", IdDocumentType.OLD_ID_CARD)
        ID_CARD   | "ESTONIAN_CITIZEN_ID_CARD" | new IdCardSession("Chuck", "Norris", "38512121212", IdDocumentType.ESTONIAN_CITIZEN_ID_CARD)
        ID_CARD   | "UNKNOWN"                  | new IdCardSession(" Chuck ", " Norris ", " 38512121212 ", IdDocumentType.UNKNOWN)
        MOBILE_ID | null                       | MobileIdFixture.sampleMobileIdSession
        SMART_ID  | null                       | SmartIdFixture.sampleSmartIdSession
    }
}
