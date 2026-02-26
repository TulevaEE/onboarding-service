package ee.tuleva.onboarding.auth.jwt

import tools.jackson.databind.json.JsonMapper
import ee.tuleva.onboarding.auth.authority.Authority
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.PrincipalService
import io.jsonwebtoken.Jwts
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.KeyStoreFixture.*
import static java.time.temporal.ChronoUnit.HOURS

class JwtAuthorizationFilterSpec extends Specification {

  private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"))

  private final JwtTokenUtil jwtTokenUtil = new JwtTokenUtil(
      keyStore(), keyStorePassword, "PARTNER AS", "TULEVA",
      partnerKeyPair.public, partnerKeyPair.public, clock)

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
        .subject("38510309519")
        .signWith(keyPair.private)
        .expiration(Date.from(clock.instant().plus(1, HOURS)))
        .claim("firstName", "Peeter")
        .claim("lastName", "Meeter")
        .claim("authorities", ["USER"])
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
    with(SecurityContextHolder.context.authentication) { authentication ->
      authentication != null
      with(authentication.principal as AuthenticatedPerson) { principal ->
        principal.firstName == "Peeter"
        principal.lastName == "Meeter"
        principal.personalCode == "38510309519"
      }
      with(authentication.authorities) { authorities ->
        authorities == [new SimpleGrantedAuthority("USER")]
      }
    }

    1 * filterChain.doFilter(request, response)
  }

  def "does not accept expired tokens"() {
    given:
    def token = Jwts.builder()
        .subject("38510309519")
        .signWith(keyPair.private)
        .expiration(Date.from(clock.instant().minus(1, HOURS)))
        .claim("firstName", "Peeter")
        .claim("lastName", "Meeter")
        .claim("authorities", ["USER"])
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
    def objectMapper = JsonMapper.builder().build()
    Map<String, Object> actualResponse = objectMapper.readValue(response.contentAsString, Map.class)
    Map<String, Object> expectedResponse = JwtTokenUtil.getExpiredTokenErrorResponse()
    assert actualResponse == expectedResponse
    0 * filterChain.doFilter(request, response)
  }

  def "does not accept expired tokens even when in security context"() {
    given:
    def token = Jwts.builder()
        .subject("38510309519")
        .signWith(keyPair.private)
        .expiration(Date.from(clock.instant().minus(1, HOURS)))
        .claim("firstName", "Peeter")
        .claim("lastName", "Meeter")
        .claim("authorities", ["USER"])
        .compact()
    def request = new MockHttpServletRequest()
    request.addHeader("Authorization", "Bearer " + token)
    def response = new MockHttpServletResponse()
    def filterChain = Mock(FilterChain)

    def principal = sampleAuthenticatedPersonAndMember().build()
    def authorities = [new SimpleGrantedAuthority(Authority.USER), new SimpleGrantedAuthority(Authority.MEMBER)]
    def authenticationToken = new UsernamePasswordAuthenticationToken(principal, token, authorities)
    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request))
    SecurityContextHolder.getContext().setAuthentication(authenticationToken)

    when:
    filter.doFilterInternal(request, response, filterChain)
    then:
    SecurityContextHolder.context.getAuthentication() == null
    response.status == HttpServletResponse.SC_UNAUTHORIZED
    response.contentType == "application/json"
    def objectMapper = JsonMapper.builder().build()
    Map<String, Object> actualResponse = objectMapper.readValue(response.contentAsString, Map.class)
    Map<String, Object> expectedResponse = JwtTokenUtil.getExpiredTokenErrorResponse()
    assert actualResponse == expectedResponse
    0 * filterChain.doFilter(request, response)
  }

  def "does not accept unsigned tokens"() {
    given:
    def token = Jwts.builder()
        .subject("38510309519")
        .expiration(Date.from(clock.instant().plus(1, HOURS)))
        .claim("firstName", "Peeter")
        .claim("lastName", "Meeter")
        .claim("authorities", ["USER"])
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
        .subject("38510309519")
        .signWith(keyPair.private)
        .expiration(Date.from(clock.instant().plus(1, HOURS)))
        .claim("firstName", "Peeter")
        .claim("lastName", "Meeter")
        .claim("authorities", ["USER"])
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

  def "works with partner handover token"() {
    given:
    def token = Jwts.builder()
        .subject("38510309519")
        .signWith(partnerKeyPair.private)
        .expiration(Date.from(clock.instant().plus(1, HOURS)))
        .claim("firstName", "Peeter")
        .claim("lastName", "Meeter")
        .claim("iss", "PARTNER AS")
        .claim("cid", "TULEVA")
        .claim("authorities", ["PARTNER"])
        .claim("tokenType", "HANDOVER")
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
    with(SecurityContextHolder.context.authentication) { authentication ->
      authentication != null
      with(authentication.principal as AuthenticatedPerson) { principal ->
        principal.firstName == "Peeter"
        principal.lastName == "Meeter"
        principal.personalCode == "38510309519"
      }
      with(authentication.authorities) { authorities ->
        authorities == [new SimpleGrantedAuthority("PARTNER")]
      }
    }

    1 * filterChain.doFilter(request, response)
  }

  def "HANDOVER tokens always get PARTNER authority only"() {
    given:
    def token = Jwts.builder()
        .subject("38510309519")
        .signWith(partnerKeyPair.private)
        .expiration(Date.from(clock.instant().plus(1, HOURS)))
        .claim("firstName", "Peeter")
        .claim("lastName", "Meeter")
        .claim("iss", "PARTNER AS")
        .claim("cid", "TULEVA")
        .claim("authorities", authorities)
        .claim("tokenType", "HANDOVER")
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
    with(SecurityContextHolder.context.authentication) { authentication ->
      authentication != null
      with(authentication.principal as AuthenticatedPerson) { principal ->
        principal.firstName == "Peeter"
        principal.lastName == "Meeter"
        principal.personalCode == "38510309519"
      }
      with(authentication.authorities) { extractedAuthorities ->
        extractedAuthorities == [new SimpleGrantedAuthority("PARTNER")]
      }
    }
    1 * filterChain.doFilter(request, response)

    where:
    authorities                   | _
    ["USER"]                      | _
    ["MEMBER"]                    | _
    ["PARTNER", "USER"]           | _
    ["PARTNER", "MEMBER"]         | _
    ["PARTNER", "USER", "MEMBER"] | _
    "USER"                        | _
    "MEMBER"                      | _
    null                          | _
  }
}
