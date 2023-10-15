package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember

class AuthServiceSpec extends Specification {
  private final AuthProvider authProvider = Mock()
  private final ApplicationEventPublisher eventPublisher = Mock()
  private final JwtTokenUtil jwtTokenUtil = Mock()
  private final GrantedAuthorityFactory grantedAuthorityFactory = Mock()
  private final AuthService authService = new AuthService(
      [authProvider], eventPublisher, jwtTokenUtil, grantedAuthorityFactory
  )

  def "provider is not used when it does not support the grant type"() {
    given:
    GrantType grantType = GrantType.SMART_ID
    String authenticationHash = "dummy"
    authProvider.supports(grantType) >> false
    when:
    authService.authenticate(grantType, authenticationHash)
    then:
    0 * authProvider.authenticate(authenticationHash)
  }

  def "provider is used when it supports the grant type"() {
    given:
    AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    List<GrantedAuthority> grantedAuthorities = [new SimpleGrantedAuthority("USER")]
    GrantType grantType = GrantType.SMART_ID
    String authenticationHash = "dummy"
    authProvider.supports(grantType) >> true
    when:
    authService.authenticate(grantType, authenticationHash)
    then:
    1 * authProvider.authenticate(authenticationHash) >> authenticatedPerson
    1 * grantedAuthorityFactory.from(authenticatedPerson) >> grantedAuthorities
    1 * eventPublisher.publishEvent(_ as BeforeTokenGrantedEvent) >> { BeforeTokenGrantedEvent event ->
      assert event.grantType == grantType
      assert event.person == authenticatedPerson
    }
    1 * jwtTokenUtil.generateToken(authenticatedPerson, grantedAuthorities) >> "dummyToken"
    1 * eventPublisher.publishEvent(_ as AfterTokenGrantedEvent) >> { AfterTokenGrantedEvent event ->
      assert event.person == authenticatedPerson
      assert event.jwtToken == "dummyToken"
    }
  }
}
