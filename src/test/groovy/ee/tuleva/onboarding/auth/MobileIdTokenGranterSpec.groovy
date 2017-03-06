package ee.tuleva.onboarding.auth

import ch.qos.logback.core.net.server.Client
import com.codeborne.security.mobileid.MobileIDSession
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserRepository
import org.springframework.security.oauth2.common.OAuth2AccessToken
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException
import org.springframework.security.oauth2.provider.ClientDetails
import org.springframework.security.oauth2.provider.ClientDetailsService
import org.springframework.security.oauth2.provider.OAuth2Authentication
import org.springframework.security.oauth2.provider.OAuth2Request
import org.springframework.security.oauth2.provider.OAuth2RequestFactory
import org.springframework.security.oauth2.provider.TokenRequest
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices
import spock.lang.Specification

import java.time.Instant

class MobileIdTokenGranterSpec extends Specification {

    MobileIdTokenGranter mobileIdTokenGranter
    AuthorizationServerTokenServices authorizationServerTokenServices = Mock(AuthorizationServerTokenServices)
    ClientDetailsService clientDetailsService = Mock(ClientDetailsService)
    OAuth2RequestFactory oAuth2RequestFactory = Mock(OAuth2RequestFactory)
    MobileIdAuthService mobileIdAuthService = Mock(MobileIdAuthService)
    UserRepository userRepository = Mock(UserRepository)
    MobileIdSessionStore mobileIdSessionStore = Mock(MobileIdSessionStore)


    def setup() {

        mobileIdTokenGranter = new MobileIdTokenGranter(
                authorizationServerTokenServices,
                clientDetailsService,
                oAuth2RequestFactory,
                mobileIdAuthService,
                userRepository,
                mobileIdSessionStore
        );

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
        1 * mobileIdSessionStore.get() >> MobileIdFixture.sampleMobileIdSession
        1 * mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession) >> false

        when:
        mobileIdTokenGranter.getAccessToken(sampleClientDetails(), Mock(TokenRequest))
        then:
        thrown MobileIdAuthNotCompleteException
    }

    def "GetAccessToken: Logging in with users who doesn't exists fails"() {
        given:
        1 * mobileIdSessionStore.get() >> MobileIdFixture.sampleMobileIdSession
        1 * mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession) >> true
        1 * userRepository.findByPersonalCode(MobileIdFixture.sampleMobileIdSession.personalCode) >> null

        when:
        mobileIdTokenGranter.getAccessToken(sampleClientDetails(), Mock(TokenRequest))
        then:
        thrown InvalidRequestException
    }

    def "GetAccessToken: Logging in with user and grant access token"() {
        given:
        1 * mobileIdSessionStore.get() >> MobileIdFixture.sampleMobileIdSession
        1 * mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession) >> true
        1 * userRepository.findByPersonalCode(MobileIdFixture.sampleMobileIdSession.personalCode) >> sampleUser()
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
        1 * mobileIdSessionStore.get() >> MobileIdFixture.sampleMobileIdSession
        1 * mobileIdAuthService.isLoginComplete(MobileIdFixture.sampleMobileIdSession) >> true
        User inActiveUser = sampleUser()
        inActiveUser.setActive(false)
        1 * userRepository.findByPersonalCode(MobileIdFixture.sampleMobileIdSession.personalCode) >> inActiveUser

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

    User sampleUser() {
        return User.builder()
                .firstName("Jordan")
                .lastName("Valdma")
                .personalCode("38812121212")
                .email("jordan.valdma@gmail.com")
                .phoneNumber("5555555")
                .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
                .updatedDate(Instant.parse("2017-01-31T10:06:01Z"))
                .memberNumber(0)
                .active(true)
                .build()
    }

}
