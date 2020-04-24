package ee.tuleva.onboarding.auth.smartid


import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.oauth2.common.OAuth2AccessToken
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException
import org.springframework.security.oauth2.provider.*
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.sampleSmartIdSession

class SmartIdTokenGranterSpec extends Specification {

    SmartIdTokenGranter smartIdTokenGranter
    AuthorizationServerTokenServices authorizationServerTokenServices = Mock(AuthorizationServerTokenServices)
    ClientDetailsService clientDetailsService = Mock(ClientDetailsService)
    OAuth2RequestFactory oAuth2RequestFactory = Mock(OAuth2RequestFactory)
    SmartIdAuthService smartIdAuthService = Mock(SmartIdAuthService)
    PrincipalService principalService = Mock(PrincipalService)
    GenericSessionStore sessionStore = Mock(GenericSessionStore)
    GrantedAuthorityFactory grantedAuthorityFactory = Mock(GrantedAuthorityFactory)
    ApplicationEventPublisher applicationEventPublisher = Mock(ApplicationEventPublisher)

    def setup() {
        smartIdTokenGranter = new SmartIdTokenGranter(
            authorizationServerTokenServices,
            clientDetailsService,
            oAuth2RequestFactory,
            smartIdAuthService,
            principalService,
            sessionStore,
            grantedAuthorityFactory,
            applicationEventPublisher
        )
    }

    def "GetAccessToken: Logging in with unknown client id is not allowed"() {
        given:
        ClientDetails clientDetails = Mock(ClientDetails) {
            1 * getClientId() >> null
        }

        when:
        smartIdTokenGranter.getAccessToken(clientDetails, Mock(TokenRequest))
        then:
        thrown InvalidRequestException
    }

    def "GetAccessToken: Logging in without a smart id session is not allowed"() {
        given:
        1 * sessionStore.get(SmartIdSession) >> Optional.of(sampleSmartIdSession)
        1 * smartIdAuthService.isLoginComplete(sampleSmartIdSession) >> false

        when:
        smartIdTokenGranter.getAccessToken(sampleClientDetails(), Mock(TokenRequest))
        then:
        thrown AuthNotCompleteException
    }

    def "GetAccessToken: Logging in with no smart id session throws exception"() {
        given:
        1 * sessionStore.get(SmartIdSession) >> Optional.empty()

        when:
        smartIdTokenGranter.getAccessToken(sampleClientDetails(), Mock(TokenRequest))
        then:
        thrown SmartIdSessionNotFoundException
    }

    def "GetAccessToken: Logging in with user and grant access token"() {
        given:
        1 * sessionStore.get(SmartIdSession) >> Optional.of(SmartIdFixture.sampleFinalSmartIdSession)
        1 * smartIdAuthService.isLoginComplete(SmartIdFixture.sampleFinalSmartIdSession) >> true
        1 * principalService.getFrom({ Person person ->
            person.personalCode == SmartIdFixture.personalCode &&
                person.firstName == SmartIdFixture.firstName &&
                person.lastName == SmartIdFixture.lastName

        }) >> AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember().build()
        ClientDetails sampleClientDetails = sampleClientDetails()
        TokenRequest tokenRequest = Mock(TokenRequest) {
            1 * createOAuth2Request(sampleClientDetails) >> Mock(OAuth2Request)
        }
        smartIdTokenGranter.getTokenServices() >> authorizationServerTokenServices
        1 * authorizationServerTokenServices.createAccessToken(_ as OAuth2Authentication) >> Mock(OAuth2AccessToken)
        1 * applicationEventPublisher.publishEvent(_ as BeforeTokenGrantedEvent)

        when:
        OAuth2AccessToken token = smartIdTokenGranter.getAccessToken(sampleClientDetails, tokenRequest)
        then:
        token != null
    }

    ClientDetails sampleClientDetails() {
        return Mock(ClientDetails) {
            1 * getClientId() >> "test"
        }
    }
}
