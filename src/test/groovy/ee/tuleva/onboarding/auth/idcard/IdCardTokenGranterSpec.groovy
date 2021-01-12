package ee.tuleva.onboarding.auth.idcard

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.idcard.exception.IdCardSessionNotFoundException
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.oauth2.common.OAuth2AccessToken
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException
import org.springframework.security.oauth2.provider.*
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices
import spock.lang.Specification

class IdCardTokenGranterSpec extends Specification {

    IdCardTokenGranter tokenGranter
    AuthorizationServerTokenServices authorizationServerTokenServices = Mock(AuthorizationServerTokenServices)
    ClientDetailsService clientDetailsService = Mock(ClientDetailsService)
    OAuth2RequestFactory oAuth2RequestFactory = Mock(OAuth2RequestFactory)
    GenericSessionStore genericSessionStore = Mock(GenericSessionStore)
    PrincipalService principalService = Mock(PrincipalService)
    GrantedAuthorityFactory grantedAuthorityFactory = Mock(GrantedAuthorityFactory)
    ApplicationEventPublisher applicationEventPublisher = Mock(ApplicationEventPublisher)

    def setup() {
        tokenGranter = new IdCardTokenGranter(
            authorizationServerTokenServices,
            clientDetailsService,
            oAuth2RequestFactory,
            genericSessionStore,
            principalService,
            grantedAuthorityFactory,
            applicationEventPublisher)
    }

    def "GetAccessToken: Logging in with no client id fails"() {
        when:
        tokenGranter.getAccessToken(Mock(ClientDetails), tokenRequest())
        then:
        thrown InvalidRequestException
    }

    def "GetAccessToken: Logging in with no id card session returns exception"() {
        given:
        genericSessionStore.get(IdCardSession) >> Optional.empty()

        when:
        tokenGranter.getAccessToken(clientDetails(), tokenRequest())
        then:
        thrown IdCardSessionNotFoundException
    }

    def "GetAccessToken: Logging in with user and grant access token"() {
        given:
        def idCardSession = new IdCardSession("Justin", "Case", "38512121212", IdDocumentType.UNKNOWN);
        1 * genericSessionStore.get(IdCardSession) >> Optional.of(idCardSession)
        1 * principalService.getFrom({ Person person ->
            person.personalCode == idCardSession.personalCode &&
                person.firstName == idCardSession.firstName &&
                person.lastName == idCardSession.lastName

        }) >> AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember().build()
        ClientDetails sampleClientDetails = clientDetails()
        TokenRequest tokenRequest = Mock(TokenRequest) {
            1 * createOAuth2Request(sampleClientDetails) >> Mock(OAuth2Request)
        }
        tokenGranter.getTokenServices() >> authorizationServerTokenServices
        1 * authorizationServerTokenServices.createAccessToken(_ as OAuth2Authentication) >> Mock(OAuth2AccessToken)
        1 * applicationEventPublisher.publishEvent(_ as BeforeTokenGrantedEvent)
        1 * applicationEventPublisher.publishEvent(_ as AfterTokenGrantedEvent)

        when:
        OAuth2AccessToken token = tokenGranter.getAccessToken(sampleClientDetails, tokenRequest)
        then:
        token != null
    }

    def clientDetails() {
        return Mock(ClientDetails) {
            1 * getClientId() >> "test"
        }
    }

    def tokenRequest() {
        Mock(TokenRequest)
    }
}
