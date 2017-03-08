package ee.tuleva.onboarding.auth.mobileid

import ee.tuleva.onboarding.auth.AuthUserService
import ee.tuleva.onboarding.auth.UserFixture
import org.springframework.security.oauth2.common.OAuth2AccessToken
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException
import org.springframework.security.oauth2.provider.*
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices
import spock.lang.Specification

class MobileIdTokenGranterSpec extends Specification {

    MobileIdTokenGranter mobileIdTokenGranter
    AuthorizationServerTokenServices authorizationServerTokenServices = Mock(AuthorizationServerTokenServices)
    ClientDetailsService clientDetailsService = Mock(ClientDetailsService)
    OAuth2RequestFactory oAuth2RequestFactory = Mock(OAuth2RequestFactory)
    MobileIdAuthService mobileIdAuthService = Mock(MobileIdAuthService)
    AuthUserService authUserService = Mock(AuthUserService)
    MobileIdSessionStore mobileIdSessionStore = Mock(MobileIdSessionStore)


    def setup() {
        mobileIdTokenGranter = new MobileIdTokenGranter(
                authorizationServerTokenServices,
                clientDetailsService,
                oAuth2RequestFactory,
                mobileIdAuthService,
                authUserService,
                mobileIdSessionStore
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
        1 * mobileIdSessionStore.get() >> Optional.of(MobileIdFixture.sampleMobileIdSession)
        1 * mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession) >> false

        when:
        mobileIdTokenGranter.getAccessToken(sampleClientDetails(), Mock(TokenRequest))
        then:
        thrown MobileIdAuthNotCompleteException
    }

    def "GetAccessToken: Logging in with no mobile id session returns null"() {
        given:
        1 * mobileIdSessionStore.get() >> Optional.empty()

        when:
        OAuth2AccessToken token = mobileIdTokenGranter.getAccessToken(sampleClientDetails(), Mock(TokenRequest))
        then:
        token == null
    }

    def "GetAccessToken: Logging in with users who doesn't exists fails"() {
        given:
        1 * mobileIdSessionStore.get() >> Optional.of(MobileIdFixture.sampleMobileIdSession)
        1 * mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession) >> true
        1 * authUserService.getByPersonalCode(MobileIdFixture.sampleMobileIdSession.personalCode) >>
                { throw new InvalidRequestException("INVALID_USER_CREDENTIALS") }

        when:
        mobileIdTokenGranter.getAccessToken(sampleClientDetails(), Mock(TokenRequest))
        then:
        thrown InvalidRequestException
    }

    def "GetAccessToken: Logging in with user and grant access token"() {
        given:
        1 * mobileIdSessionStore.get() >> Optional.of(MobileIdFixture.sampleMobileIdSession)
        1 * mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession) >> true
        1 * authUserService.getByPersonalCode(MobileIdFixture.sampleMobileIdSession.personalCode) >> UserFixture.sampleUser()
        ClientDetails sampleClientDetails = sampleClientDetails()
        TokenRequest tokenRequest = Mock(TokenRequest) {
            1 * createOAuth2Request(sampleClientDetails) >> Mock(OAuth2Request)
        }
        mobileIdTokenGranter.getTokenServices() >> authorizationServerTokenServices
        1 * authorizationServerTokenServices.createAccessToken(_ as OAuth2Authentication) >> Mock(OAuth2AccessToken)

        when:
        OAuth2AccessToken token = mobileIdTokenGranter.getAccessToken(sampleClientDetails, tokenRequest)
        then:
        token != null
    }


    def "GetAccessToken: Logging in with inactive user fails"() {
        given:
        1 * mobileIdSessionStore.get() >> Optional.of(MobileIdFixture.sampleMobileIdSession)
        1 * mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession) >> true
        1 * authUserService.getByPersonalCode(MobileIdFixture.sampleMobileIdSession.personalCode) >>
                { throw new InvalidRequestException("INVALID_USER_CREDENTIALS") }

        when:
        mobileIdTokenGranter.getAccessToken(sampleClientDetails(), Mock(TokenRequest))
        then:
        thrown InvalidRequestException
    }

    ClientDetails sampleClientDetails() {
        return Mock(ClientDetails) {
            1 * getClientId() >> "test"
        }
    }

}
