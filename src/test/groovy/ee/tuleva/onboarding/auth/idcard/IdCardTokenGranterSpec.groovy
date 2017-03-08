package ee.tuleva.onboarding.auth.idcard

import ee.tuleva.onboarding.auth.AuthUserService
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException
import org.springframework.security.oauth2.provider.ClientDetails
import org.springframework.security.oauth2.provider.ClientDetailsService
import org.springframework.security.oauth2.provider.OAuth2RequestFactory
import org.springframework.security.oauth2.provider.TokenRequest
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices
import spock.lang.Specification

class IdCardTokenGranterSpec extends Specification {

    IdCardTokenGranter tokenGranter
    AuthorizationServerTokenServices authorizationServerTokenServices = Mock(AuthorizationServerTokenServices)
    ClientDetailsService clientDetailsService = Mock(ClientDetailsService)
    OAuth2RequestFactory oAuth2RequestFactory = Mock(OAuth2RequestFactory)
    IdCardSessionStore idCardSessionStore = Mock(IdCardSessionStore)
    AuthUserService authUserService = Mock(AuthUserService)

    def setup() {
        tokenGranter = new IdCardTokenGranter(
                authorizationServerTokenServices,
                clientDetailsService,
                oAuth2RequestFactory,
                idCardSessionStore,
                authUserService)
    }

    def "GetAccessToken: Logging in with no client id fails"() {
        when:
        tokenGranter.getAccessToken(Mock(ClientDetails), tokenRequest())
        then:
        thrown InvalidRequestException
    }

    def "GetAccessToken: Logging in with no id card session returns null"() {
        given:
        idCardSessionStore.get() >> Optional.empty()

        when:
        def token = tokenGranter.getAccessToken(clientDetails(), tokenRequest())
        then:
        token == null
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
