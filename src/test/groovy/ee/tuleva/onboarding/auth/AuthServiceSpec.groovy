package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil
import ee.tuleva.onboarding.auth.jwt.TokenType
import ee.tuleva.onboarding.auth.principal.ActingAs
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.PrincipalService
import io.jsonwebtoken.ExpiredJwtException
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.GrantType.*

class AuthServiceSpec extends Specification {
  private final AuthProvider authProvider = Mock()
  private final ApplicationEventPublisher eventPublisher = Mock()
  private final JwtTokenUtil jwtTokenUtil = Mock()
  private final TokenService tokenService = Mock()
  private final PrincipalService principalService = Mock()
  private final AuthService authService = new AuthService(
      [authProvider], eventPublisher, jwtTokenUtil, tokenService, principalService
  )

  def "successful authentication generates access and refresh tokens"() {
    given:
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        GrantType grantType = SMART_ID
        String authenticationHash = "dummy"
        def tokens = new AuthenticationTokens("jwtToken", "refreshToken")
        authProvider.supports(grantType) >> true
        authProvider.authenticate(authenticationHash) >> authenticatedPerson
        tokenService.generateTokens(authenticatedPerson) >> tokens

    when:
        AuthenticationTokens result = authService.authenticate(grantType, authenticationHash)

    then:
        result.accessToken == "jwtToken"
        result.refreshToken == "refreshToken"
        1 * eventPublisher.publishEvent(_ as BeforeTokenGrantedEvent)
        1 * eventPublisher.publishEvent(_ as AfterTokenGrantedEvent)
  }

  def "refresh token successfully generates new tokens"() {
    given:
        String refreshToken = "validRefreshToken"
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
        jwtTokenUtil.getTypeFromToken(refreshToken) >> TokenType.REFRESH
        jwtTokenUtil.getPersonFromToken(refreshToken) >> authenticatedPerson
        jwtTokenUtil.getAttributesFromToken(refreshToken) >> [:]
        jwtTokenUtil.getActingAsFromToken(refreshToken) >> null
        principalService.getFrom(authenticatedPerson, [:], null) >> authenticatedPerson
        tokenService.generateTokens(authenticatedPerson) >> new AuthenticationTokens("newAccessToken", "newRefreshToken")

    when:
        AuthenticationTokens tokens = authService.refreshToken(refreshToken)

    then:
        tokens.accessToken == "newAccessToken"
        tokens.refreshToken == refreshToken
  }

  def "refresh token preserves actingAs company"() {
    given:
        String refreshToken = "validRefreshToken"
        def company = new ActingAs.Company("12345678")
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember()
            .actingAs(company)
            .build()
        jwtTokenUtil.getTypeFromToken(refreshToken) >> TokenType.REFRESH
        jwtTokenUtil.getPersonFromToken(refreshToken) >> authenticatedPerson
        jwtTokenUtil.getAttributesFromToken(refreshToken) >> [:]
        jwtTokenUtil.getActingAsFromToken(refreshToken) >> company
        principalService.getFrom(authenticatedPerson, [:], company) >> authenticatedPerson
        tokenService.generateTokens(authenticatedPerson) >> new AuthenticationTokens("newAccessToken", "newRefreshToken")

    when:
        AuthenticationTokens tokens = authService.refreshToken(refreshToken)

    then:
        tokens.accessToken == "newAccessToken"
        tokens.refreshToken == refreshToken
  }


  def "provider is not used when it does not support the grant type"() {
    given:
    def grantType = SMART_ID
    String authenticationHash = "dummy"
    authProvider.supports(grantType) >> false
    when:
    authService.authenticate(grantType, authenticationHash)
    then:
    0 * authProvider.authenticate(authenticationHash)
  }

  def "provider is used when it supports the grant type"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def grantType = SMART_ID
    String authenticationHash = "dummy"
    authProvider.supports(grantType) >> true
    when:
    authService.authenticate(grantType, authenticationHash)
    then:
    1 * authProvider.authenticate(authenticationHash) >> authenticatedPerson
    1 * eventPublisher.publishEvent(_ as BeforeTokenGrantedEvent) >> { BeforeTokenGrantedEvent event ->
      assert event.grantType == grantType
      assert event.person == authenticatedPerson
    }
    1 * tokenService.generateTokens(authenticatedPerson) >> new AuthenticationTokens("dummyToken", "refreshToken")
    1 * eventPublisher.publishEvent(_ as AfterTokenGrantedEvent) >> { AfterTokenGrantedEvent event ->
      assert event.person == authenticatedPerson
      assert event.tokens.accessToken() == "dummyToken"
      assert event.tokens.refreshToken() == "refreshToken"
    }
  }

  def "authenticate method handles null authentication hash gracefully"() {
    given:
        def grantType = SMART_ID
        String authenticationHash = null

    when:
        AuthenticationTokens tokens = authService.authenticate(grantType, authenticationHash)

    then:
        tokens == null
  }

  def "authenticate returns null when no providers support the grant type"() {
    given:
        def grantType = SMART_ID
        String authenticationHash = "dummy"
        authProvider.supports(grantType) >> false

    when:
        AuthenticationTokens tokens = authService.authenticate(grantType, authenticationHash)

    then:
        tokens == null
  }

  def "refreshToken throws IllegalArgumentException for non-refresh token types"() {
    given:
        String refreshToken = "invalidTypeToken"
        jwtTokenUtil.getTypeFromToken(refreshToken) >> TokenType.ACCESS

    when:
        authService.refreshToken(refreshToken)

    then:
        thrown(IllegalArgumentException)
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
