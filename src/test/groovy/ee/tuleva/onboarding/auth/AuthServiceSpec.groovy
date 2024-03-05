package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil
import ee.tuleva.onboarding.auth.jwt.TokenType
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.PrincipalService
import io.jsonwebtoken.ExpiredJwtException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

import javax.ws.rs.BadRequestException

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember

class AuthServiceSpec extends Specification {
  private final AuthProvider authProvider = Mock()
  private final ApplicationEventPublisher eventPublisher = Mock()
  private final JwtTokenUtil jwtTokenUtil = Mock()
  private final GrantedAuthorityFactory grantedAuthorityFactory = Mock()
  private final PrincipalService principalService = Mock()
  private final AuthService authService = new AuthService(
      [authProvider], eventPublisher, jwtTokenUtil, grantedAuthorityFactory, principalService
  )

  def "successful authentication generates access and refresh tokens"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        List<GrantedAuthority> grantedAuthorities = [new SimpleGrantedAuthority("USER")]
        GrantType grantType = GrantType.SMART_ID
        String authenticationHash = "dummy"
        String jwtToken = "jwtToken"
        String refreshToken = "refreshToken"
        authProvider.supports(grantType) >> true
        authProvider.authenticate(authenticationHash) >> authenticatedPerson
        grantedAuthorityFactory.from(authenticatedPerson) >> grantedAuthorities
        jwtTokenUtil.generateAccessToken(authenticatedPerson, grantedAuthorities) >> jwtToken
        jwtTokenUtil.generateRefreshToken(authenticatedPerson, grantedAuthorities) >> refreshToken

    when:
        AccessAndRefreshToken tokens = authService.authenticate(grantType, authenticationHash)

    then:
        tokens.accessToken == jwtToken
        tokens.refreshToken == refreshToken
        1 * eventPublisher.publishEvent(_ as BeforeTokenGrantedEvent)
        1 * eventPublisher.publishEvent(_ as AfterTokenGrantedEvent)
  }

  def "refresh token successfully generates new tokens"() {
    given:
        String refreshToken = "validRefreshToken"
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        List<GrantedAuthority> grantedAuthorities = [new SimpleGrantedAuthority("USER")]
        TokenType tokenType = TokenType.REFRESH
        jwtTokenUtil.getTypeFromToken(refreshToken) >> tokenType
        jwtTokenUtil.getPersonFromToken(refreshToken) >> authenticatedPerson
        jwtTokenUtil.getAttributesFromToken(refreshToken) >> [:]
        principalService.getFrom(authenticatedPerson, [:]) >> authenticatedPerson
        grantedAuthorityFactory.from(authenticatedPerson) >> grantedAuthorities
        String newAccessToken = "newAccessToken"
        1 * jwtTokenUtil.generateAccessToken(authenticatedPerson, grantedAuthorities) >> newAccessToken
        0 * jwtTokenUtil.generateRefreshToken(authenticatedPerson, grantedAuthorities)

    when:
        AccessAndRefreshToken tokens = authService.refreshToken(refreshToken)

    then:
        tokens.accessToken == newAccessToken
        tokens.refreshToken == refreshToken
  }


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
    1 * jwtTokenUtil.generateAccessToken(authenticatedPerson, grantedAuthorities) >> "dummyToken"
    1 * jwtTokenUtil.generateRefreshToken(authenticatedPerson, grantedAuthorities) >> "refreshToken"
    1 * eventPublisher.publishEvent(_ as AfterTokenGrantedEvent) >> { AfterTokenGrantedEvent event ->
      assert event.person == authenticatedPerson
      assert event.tokens.accessToken() == "dummyToken"
      assert event.tokens.refreshToken() == "refreshToken"
    }
  }

  def "authenticate method handles null authentication hash gracefully"() {
    given:
        GrantType grantType = GrantType.SMART_ID
        String authenticationHash = null

    when:
        AccessAndRefreshToken tokens = authService.authenticate(grantType, authenticationHash)

    then:
        tokens == null
  }

  def "authenticate returns null when no providers support the grant type"() {
    given:
        GrantType grantType = GrantType.SMART_ID
        String authenticationHash = "dummy"
        authProvider.supports(grantType) >> false

    when:
        AccessAndRefreshToken tokens = authService.authenticate(grantType, authenticationHash)

    then:
        tokens == null
  }

  def "refreshToken throws BadRequestException for non-refresh token types"() {
    given:
        String refreshToken = "invalidTypeToken"
        jwtTokenUtil.getTypeFromToken(refreshToken) >> TokenType.ACCESS

    when:
        authService.refreshToken(refreshToken)

    then:
        thrown(BadRequestException)
  }

  def "refreshToken throws ExpiredRefreshJwtException for expired refresh tokens"() {
    given:
        String refreshToken = "expiredRefreshToken"
        jwtTokenUtil.getTypeFromToken(refreshToken) >> TokenType.REFRESH
        jwtTokenUtil.getPersonFromToken(refreshToken) >> { throw new ExpiredJwtException(null, null, null) }

    when:
        authService.refreshToken(refreshToken)

    then:
        thrown(ExpiredRefreshJwtException)
  }
}
