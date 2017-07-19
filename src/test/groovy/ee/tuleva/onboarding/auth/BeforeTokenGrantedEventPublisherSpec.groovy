package ee.tuleva.onboarding.auth

import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.oauth2.provider.OAuth2Authentication
import spock.lang.Specification

class BeforeTokenGrantedEventPublisherSpec extends Specification {

    ApplicationEventPublisher applicationEventPublisher = Mock(ApplicationEventPublisher)
    BeforeTokenGrantedEventPublisher service =
            new BeforeTokenGrantedEventPublisher(applicationEventPublisher)


    def "Publish: Publishes the event"() {
        given:
        OAuth2Authentication sampleOAuth2Authentication = Mock(OAuth2Authentication)

        when:
        service.publish(sampleOAuth2Authentication)
        then:
        1 * applicationEventPublisher
                .publishEvent({BeforeTokenGrantedEvent beforeTokenGrantedEvent ->
            beforeTokenGrantedEvent.authentication == sampleOAuth2Authentication
        })
    }
}
