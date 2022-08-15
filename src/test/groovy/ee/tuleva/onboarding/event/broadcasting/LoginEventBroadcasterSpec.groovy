package ee.tuleva.onboarding.event.broadcasting

import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.idcard.IdCardSession
import ee.tuleva.onboarding.auth.idcard.IdDocumentType
import ee.tuleva.onboarding.event.TrackableEventPublisher
import ee.tuleva.onboarding.event.TrackableEventType
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.GrantType.*
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.mobileid.MobileIdFixture.sampleMobileIdSession
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.sampleSmartIdSession

class LoginEventBroadcasterSpec extends Specification {

    TrackableEventPublisher trackableEventPublisher = Mock(TrackableEventPublisher)
    LoginEventBroadcaster service = new LoginEventBroadcaster(trackableEventPublisher)

    def "OnBeforeTokenGrantedEvent: Broadcast login event"(GrantType grantType, String document, Object credentials) {
        given:
        def samplePerson = samplePerson()

        def sampleAuthentication = Mock(OAuth2Authentication, {
            getUserAuthentication() >> Mock(Authentication, {
                getCredentials() >> credentials
            })
        })

        def event = new BeforeTokenGrantedEvent(this, samplePerson, sampleAuthentication, grantType)

        when:
        service.onBeforeTokenGrantedEvent(event)

        then:
        if (document != null) {
            1 * trackableEventPublisher.publish(samplePerson.personalCode, TrackableEventType.LOGIN, "method=$grantType", "document=$document")
        } else {
            1 * trackableEventPublisher.publish(samplePerson.personalCode, TrackableEventType.LOGIN, "method=$grantType")
        }

        where:
        grantType | document                   | credentials
        ID_CARD   | "DIGITAL_ID_CARD"          | new IdCardSession("Chuck", "Norris", "38512121212", IdDocumentType.DIGITAL_ID_CARD)
        ID_CARD   | "OLD_ID_CARD"              | new IdCardSession("Chuck", "Norris", "38512121212", IdDocumentType.OLD_ID_CARD)
        ID_CARD   | "ESTONIAN_CITIZEN_ID_CARD" | new IdCardSession("Chuck", "Norris", "38512121212", IdDocumentType.ESTONIAN_CITIZEN_ID_CARD)
        ID_CARD   | "DIPLOMATIC_ID_CARD"       | new IdCardSession(" Chuck ", " Norris ", " 38512121212 ", IdDocumentType.DIPLOMATIC_ID_CARD)
        MOBILE_ID | null                       | sampleMobileIdSession
        SMART_ID  | null                       | sampleSmartIdSession
    }
}
