package ee.tuleva.onboarding.auth.jwt

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.auth.KeyStoreFixture
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.PrincipalService
import io.jsonwebtoken.Jwts
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

import javax.servlet.FilterChain
import javax.servlet.http.HttpServletResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.KeyStoreFixture.keyStore
import static ee.tuleva.onboarding.auth.KeyStoreFixture.keyStorePassword
import static java.time.temporal.ChronoUnit.HOURS

class JwtAuthorizationFilterSpec extends Specification {

  private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"))

  private final JwtTokenUtil jwtTokenUtil = new JwtTokenUtil(keyStore(), keyStorePassword, clock)

  private final PrincipalService principalService = Mock()

  private final JwtAuthorizationFilter filter = new JwtAuthorizationFilter(jwtTokenUtil, principalService)

  def cleanup() {
    SecurityContextHolder.clearContext()
  }

  def "does not do anything on empty request"() {
    given:
    def request = new MockHttpServletRequest()
    def response = new MockHttpServletResponse()
    def filterChain = Mock(FilterChain)
    when:
    filter.doFilterInternal(request, response, filterChain)
    then:
    SecurityContextHolder.context.getAuthentication() == null
    1 * filterChain.doFilter(request, response)
  }

  def "reads user info from a valid authorization header"() {
    given:
    def token = Jwts.builder()
        .setSubject("38510309519")
        .signWith(KeyStoreFixture.keyPair.private)
        .setExpiration(Date.from(clock.instant().plus(1, HOURS)))
        .claim("firstName", "Peeter")
        .claim("lastName", "Meeter")
        .claim("authorities", new String[]{"USER"})
        .claim("tokenType", "ACCESS")
        .compact()
    def person = sampleAuthenticatedPersonAndMember()
        .firstName("Peeter")
        .lastName("Meeter")
        .personalCode("38510309519")
        .build()
    def request = new MockHttpServletRequest()
    request.addHeader("Authorization", "Bearer " + token)
    def response = new MockHttpServletResponse()
    def filterChain = Mock(FilterChain)
    principalService.getFrom(_, _) >> person
    when:
    filter.doFilterInternal(request, response, filterChain)
    then:
    SecurityContextHolder.context.getAuthentication() != null
    (SecurityContextHolder.context.getAuthentication().getPrincipal() as AuthenticatedPerson).firstName == "Peeter"
    (SecurityContextHolder.context.getAuthentication().getPrincipal() as AuthenticatedPerson).lastName == "Meeter"
    (SecurityContextHolder.context.getAuthentication().getPrincipal() as AuthenticatedPerson).personalCode == "38510309519"
    SecurityContextHolder.context.getAuthentication().getAuthorities() == [new SimpleGrantedAuthority("USER")]
    1 * filterChain.doFilter(request, response)
  }

  def "does not accept expired tokens"() {
    given:
    def token = Jwts.builder()
        .setSubject("38510309519")
        .signWith(KeyStoreFixture.keyPair.private)
        .setExpiration(Date.from(clock.instant().minus(1, HOURS)))
        .claim("firstName", "Peeter")
        .claim("lastName", "Meeter")
        .claim("authorities", new String[]{"USER"})
        .compact()
    def request = new MockHttpServletRequest()
    request.addHeader("Authorization", "Bearer " + token)
    def response = new MockHttpServletResponse()
    def filterChain = Mock(FilterChain)
    when:
    filter.doFilterInternal(request, response, filterChain)
    then:
    SecurityContextHolder.context.getAuthentication() == null
    response.status == HttpServletResponse.SC_UNAUTHORIZED
    response.contentType == "application/json"
    def objectMapper = new ObjectMapper()
    Map<String, Object> actualResponse = objectMapper.readValue(response.contentAsString, Map.class)
    Map<String, Object> expectedResponse = JwtTokenUtil.getExpiredTokenErrorResponse()
    assert actualResponse == expectedResponse
    0 * filterChain.doFilter(request, response)
  }

  def "does not accept unsigned tokens"() {
    given:
    def token = Jwts.builder()
        .setSubject("38510309519")
        .setExpiration(Date.from(clock.instant().plus(1, HOURS)))
        .claim("firstName", "Peeter")
        .claim("lastName", "Meeter")
        .claim("authorities", new String[]{"USER"})
        .compact()
    def request = new MockHttpServletRequest()
    request.addHeader("Authorization", "Bearer " + token)
    def response = new MockHttpServletResponse()
    def filterChain = Mock(FilterChain)
    when:
    filter.doFilterInternal(request, response, filterChain)
    then:
    SecurityContextHolder.context.getAuthentication() == null
    1 * filterChain.doFilter(request, response)
  }

  def "does not accept refresh tokens"() {
    given:
        def token = Jwts.builder()
            .setSubject("38510309519")
            .signWith(KeyStoreFixture.keyPair.private)
            .setExpiration(Date.from(clock.instant().plus(1, HOURS)))
            .claim("firstName", "Peeter")
            .claim("lastName", "Meeter")
            .claim("authorities", new String[]{"USER"})
            .claim("tokenType", "REFRESH")
            .compact()
        def request = new MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer " + token)
        def response = new MockHttpServletResponse()
        def filterChain = Mock(FilterChain)
    when:
        filter.doFilterInternal(request, response, filterChain)
    then:
        SecurityContextHolder.context.getAuthentication() == null
        0 * filterChain.doFilter(request, response)
  }
}
