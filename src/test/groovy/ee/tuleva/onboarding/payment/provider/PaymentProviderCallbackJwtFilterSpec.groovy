package ee.tuleva.onboarding.payment.provider

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken
import org.springframework.security.oauth2.provider.OAuth2Authentication
import org.springframework.security.oauth2.provider.token.DefaultTokenServices
import org.springframework.security.oauth2.provider.token.store.JdbcTokenStore
import spock.lang.Specification

import javax.servlet.FilterChain

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedCallbackFinalizedTokenWithCorrectIdCode

class PaymentProviderCallbackJwtFilterSpec extends Specification {

  AuthenticationManager authenticationManager = Mock()
  PaymentProviderCallbackService paymentProviderCallbackService = Mock()
  JdbcTokenStore tokenStore = Mock()
  DefaultTokenServices tokenServices = Mock()
  FilterChain filterChain = Mock()
  MockHttpServletRequest request
  MockHttpServletResponse response

  PaymentProviderCallbackJwtFilter jwtFilter =
      new PaymentProviderCallbackJwtFilter(authenticationManager, paymentProviderCallbackService, tokenStore, tokenServices)

  def setup() {
    request = new MockHttpServletRequest()
    response = new MockHttpServletResponse()
  }

  def "will do nothing when endpoint does not match and no jwt"() {
    when:
    jwtFilter.doFilterInternal(request, response, filterChain)

    then:
    1 * filterChain.doFilter(request, response)
    SecurityContextHolder.getContext().getAuthentication() == null
  }

  def "will authenticate with the jwt"() {
    given:
    def user = sampleUser().build()
    def token = aSerializedCallbackFinalizedTokenWithCorrectIdCode

    request.setRequestURI("/v1/payments/success")
    request.addParameter("payment_token", token)

    def accessToken = new DefaultOAuth2AccessToken("existing bearer token")
    def mockAuthentication = Mock(OAuth2Authentication)

    paymentProviderCallbackService.processToken(token) >> Optional.of(aNewPayment())
    tokenStore.findTokensByUserName(user.personalCode) >> [accessToken]
    tokenServices.loadAuthentication(accessToken.value) >> mockAuthentication

    when:
    jwtFilter.doFilterInternal(request, response, filterChain)
    def authentication = SecurityContextHolder.getContext().getAuthentication()

    then:
    1 * filterChain.doFilter(request, response)
    authentication == mockAuthentication
  }

}
