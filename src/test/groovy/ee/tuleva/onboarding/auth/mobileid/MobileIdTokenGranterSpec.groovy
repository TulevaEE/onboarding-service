package ee.tuleva.onboarding.auth.mobileid

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
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

import static ee.tuleva.onboarding.auth.mobileid.MobileIdFixture.sampleMobileIdSession

class MobileIdTokenGranterSpec extends Specification {

    MobileIdTokenGranter mobileIdTokenGranter
    AuthorizationServerTokenServices authorizationServerTokenServices = Mock(AuthorizationServerTokenServices)
    ClientDetailsService clientDetailsService = Mock(ClientDetailsService)
    OAuth2RequestFactory oAuth2RequestFactory = Mock(OAuth2RequestFactory)
    MobileIdAuthService mobileIdAuthService = Mock(MobileIdAuthService)
    PrincipalService principalService = Mock(PrincipalService)
    GenericSessionStore sessionStore = Mock(GenericSessionStore)
    GrantedAuthorityFactory grantedAuthorityFactory = Mock(GrantedAuthorityFactory)
    ApplicationEventPublisher applicationEventPublisher = Mock(ApplicationEventPublisher)

    def setup() {
        mobileIdTokenGranter = new MobileIdTokenGranter(
            authorizationServerTokenServices,
            clientDetailsService,
            oAuth2RequestFactory,
            mobileIdAuthService,
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
        mobileIdTokenGranter.getAccessToken(clientDetails, Mock(TokenRequest))
        then:
        thrown InvalidRequestException
    }

    def "GetAccessToken: Logging in without a mobile id session is not allowed"() {
        given:
        1 * sessionStore.get(MobileIDSession) >> Optional.of(sampleMobileIdSession)
        1 * mobileIdAuthService.isLoginComplete(sampleMobileIdSession) >> false

        when:
        mobileIdTokenGranter.getAccessToken(sampleClientDetails(), Mock(TokenRequest))
        then:
        thrown AuthNotCompleteException
    }

    def "GetAccessToken: Logging in with no mobile id session throws exception"() {
        given:
        1 * sessionStore.get(MobileIDSession) >> Optional.empty()

        when:
        mobileIdTokenGranter.getAccessToken(sampleClientDetails(), Mock(TokenRequest))
        then:
        thrown MobileIdSessionNotFoundException
    }

    def "GetAccessToken: Logging in with user and grant access token"() {
        given:
        1 * sessionStore.get(MobileIDSession) >> Optional.of(sampleMobileIdSession)
        1 * mobileIdAuthService.isLoginComplete(sampleMobileIdSession) >> true
        1 * principalService.getFrom({ Person person ->
            person.personalCode == sampleMobileIdSession.personalCode &&
                person.firstName == sampleMobileIdSession.firstName &&
                person.lastName == sampleMobileIdSession.lastName

        }) >> AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember().build()
        ClientDetails sampleClientDetails = sampleClientDetails()
        TokenRequest tokenRequest = Mock(TokenRequest) {
            1 * createOAuth2Request(sampleClientDetails) >> Mock(OAuth2Request)
        }
        mobileIdTokenGranter.getTokenServices() >> authorizationServerTokenServices
        1 * authorizationServerTokenServices.createAccessToken(_ as OAuth2Authentication) >> Mock(OAuth2AccessToken)
        1 * applicationEventPublisher.publishEvent(_ as BeforeTokenGrantedEvent)
        1 * applicationEventPublisher.publishEvent(_ as AfterTokenGrantedEvent)

        when:
        OAuth2AccessToken token = mobileIdTokenGranter.getAccessToken(sampleClientDetails, tokenRequest)
        then:
        token != null
    }

    ClientDetails sampleClientDetails() {
        return Mock(ClientDetails) {
            1 * getClientId() >> "test"
        }
    }

}
