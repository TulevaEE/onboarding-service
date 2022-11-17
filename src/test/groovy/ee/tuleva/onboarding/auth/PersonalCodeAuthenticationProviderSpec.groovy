package ee.tuleva.onboarding.auth


import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings
import org.springframework.security.oauth2.server.authorization.context.ProviderContext
import org.springframework.security.oauth2.server.authorization.context.ProviderContextHolder
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.OAuth2Fixture.aPersonalCodeAuthentication
import static ee.tuleva.onboarding.time.TestClockHolder.now
import static org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER

class PersonalCodeAuthenticationProviderSpec extends Specification {

  OAuth2AuthorizationService oAuth2AuthorizationService = Mock()
  ApplicationEventPublisher applicationEventPublisher = Mock()
  OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository = Mock()
  RegisteredClientRepository registeredClientRepository = Mock()
  OAuth2TokenGenerator<OAuth2AccessToken> tokenGenerator = Mock()
  PersonalCodeAuthenticationProvider personalCodeAuthenticationProvider

  def setup() {
    personalCodeAuthenticationProvider = new PersonalCodeAuthenticationProvider(
        oAuth2AuthorizationService, registeredClientRepository,
        applicationEventPublisher, oAuth2AuthorizedClientRepository, tokenGenerator)

    MockHttpServletRequest request = new MockHttpServletRequest()
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

    SecurityContext context = SecurityContextHolder.createEmptyContext()
    context.setAuthentication(Mock(OAuth2ClientAuthenticationToken))
    SecurityContextHolder.context = context
  }

  def cleanup() {
    ProviderContextHolder.resetProviderContext()
    RequestContextHolder.resetRequestAttributes()
    SecurityContextHolder.clearContext()
  }

  def "only authenticates personal code authentication"() {
    when:
    personalCodeAuthenticationProvider.authenticate(new TestingAuthenticationToken("test", "password"))
    then:
    thrown(ClassCastException.class)
  }

  def "only supports personal code authentication"() {
    expect:
    personalCodeAuthenticationProvider.supports(PersonalCodeAuthentication)
    !personalCodeAuthenticationProvider.supports(TestingAuthenticationToken)
  }

  def "throws when client not found"() {
    when:
    1 * registeredClientRepository.findByClientId("client-id") >> null
    personalCodeAuthenticationProvider.authenticate(aPersonalCodeAuthentication())
    then:
    thrown(OAuth2AuthenticationException.class)
  }

  def "throws when grant type not supported"() {
    given:
    RegisteredClient mockClient = Mock()
    when:
    1 * registeredClientRepository.findByClientId("client-id") >> mockClient
    1 * mockClient.authorizationGrantTypes >> [AuthorizationGrantType.AUTHORIZATION_CODE]
    personalCodeAuthenticationProvider.authenticate(aPersonalCodeAuthentication())
    then:
    thrown(OAuth2AuthenticationException.class)
  }

  def "saves authorization token"() {
    given:
    RegisteredClient mockClient = Mock()
    OAuth2AccessToken accessToken = new OAuth2AccessToken(BEARER, "dummy", now, now.plusSeconds(3600))

    ProviderContextHolder.providerContext = new ProviderContext(ProviderSettings.builder().build(), { "" })
    1 * registeredClientRepository.findByClientId("client-id") >> mockClient
    _ * mockClient.getScopes() >> ["demo"]
    _ * mockClient.getClientId() >> "client-id"
    1 * mockClient.authorizationGrantTypes >> [GrantType.SMART_ID]
    1 * tokenGenerator.generate(_) >> accessToken
    when:
    personalCodeAuthenticationProvider.authenticate(aPersonalCodeAuthentication())
    then:
    1 * oAuth2AuthorizationService.save(_)
  }
}
