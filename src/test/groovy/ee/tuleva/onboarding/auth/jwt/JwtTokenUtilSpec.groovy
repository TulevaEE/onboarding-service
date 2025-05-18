package ee.tuleva.onboarding.auth.jwt

import ee.tuleva.onboarding.auth.partner.PartnerPublicKeyConfiguration
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.Person
import io.jsonwebtoken.ExpiredJwtException
import org.springframework.core.io.ClassPathResource
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

import java.security.PublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static ee.tuleva.onboarding.auth.jwt.TokenType.*
import static java.time.temporal.ChronoUnit.HOURS

class JwtTokenUtilSpec extends Specification {

  PartnerPublicKeyConfiguration partnerPublicKeyConfiguration = new PartnerPublicKeyConfiguration()
  PublicKey partnerPublicKey =
      partnerPublicKeyConfiguration.partnerPublicKey1(new ClassPathResource("test-partner-public-key.pem"))
  Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"))

  JwtTokenUtil jwtTokenUtil = new JwtTokenUtil(
      new ClassPathResource("test-jwt-keystore.p12"),
      "Kalamaja123".toCharArray(),
      "PARTNER AS",
      "TULEVA",
      partnerPublicKey,
      partnerPublicKey,
      clock)

  def "generates service token"() {
    when:
    String token = jwtTokenUtil.generateServiceToken()
    then:
    token == "eyJhbGciOiJSUzI1NiJ9.eyJ0b2tlblR5cGUiOiJBQ0NFU1MiLCJhdXRob3JpdGllcyI6WyJTRVJWSUNFIl0sInN1YiI6Im9uYm9hcmRpbmctc2VydmljZSIsImlhdCI6MCwiZXhwIjoxODAwfQ.O5zwOqkAfeRxi5WNvabdc_oNawRzHs7AjC9pdxdU3p0WtOd7KxqdgxryNFK3GHhkRgRIi7V7lCcHBRrrsIlRFCa78C3Q-2D6rXucpcqtvDCG1kNSyOK_OkCpdvmONjNrPYlDZY5KIc-0KB7I9uFf_qssm1qoha094hAXnUFzEQpHll2zZtgMiVIGGF-rnBdupP6KtS5X8t9JSFliiZxFTSUf15QwNv25YJEIbgPnUA4C9UrdQyUU2bSKU8eUHzHHUuabN6YAzTgS0Kw46WyaAe4ucP2hXkYzS1Xeg2dTEG-re49fSNaHq-5QiLHrfl45ztp6ZzjiGH18ix9pW2jCdA"
  }

  def "generates user token"() {
    given:
    AuthenticatedPerson person = AuthenticatedPerson.builder()
        .firstName("Peeter")
        .lastName("Meeter")
        .personalCode("38812121215")
        .build()
    List<GrantedAuthority> authorities = [new SimpleGrantedAuthority("USER")]
    when:
    String token = jwtTokenUtil.generateAccessToken(person, authorities)
    Person parsed = jwtTokenUtil.getPersonFromToken(token)
    TokenType tokenType = jwtTokenUtil.getTypeFromToken(token)

    then:
    parsed.personalCode == "38812121215"
    parsed.firstName == "Peeter"
    parsed.lastName == "Meeter"
    tokenType == ACCESS
  }

  def "generates refresh token"() {
    given:
        AuthenticatedPerson person = AuthenticatedPerson.builder()
            .firstName("Peeter")
            .lastName("Meeter")
            .personalCode("38812121215")
            .build()
        List<GrantedAuthority> authorities = [new SimpleGrantedAuthority("USER")]

    when:
        String token = jwtTokenUtil.generateRefreshToken(person, authorities)
        Person parsed = jwtTokenUtil.getPersonFromToken(token)
        TokenType tokenType = jwtTokenUtil.getTypeFromToken(token)

    then:
        parsed.personalCode == "38812121215"
        parsed.firstName == "Peeter"
        parsed.lastName == "Meeter"
        tokenType == REFRESH
  }

  def "extracts attributes and authorities from token"() {
    given:
        AuthenticatedPerson person = AuthenticatedPerson.builder()
            .firstName("Peeter")
            .lastName("Meeter")
            .personalCode("38812121215")
            .attributes(Map.of("email", "peeter@meeter.com"))
            .build()
        List<GrantedAuthority> authorities = [new SimpleGrantedAuthority("USER")]

    when:
        String token = jwtTokenUtil.generateAccessToken(person, authorities)
        Map<String, String> attributes = jwtTokenUtil.getAttributesFromToken(token)
        List<String> extractedAuthorities = jwtTokenUtil.getAuthorities(token)

    then:
        attributes.get("email") == "peeter@meeter.com"
        extractedAuthorities == ["USER"]
  }

  def "handles expired token"() {
    given:
        AuthenticatedPerson person = AuthenticatedPerson.builder()
            .firstName("Peeter")
            .lastName("Meeter")
            .personalCode("38812121215")
            .build()
        List<GrantedAuthority> authorities = [new SimpleGrantedAuthority("USER")]
        Clock pastClock = Clock.fixed(Instant.EPOCH.minus(2, HOURS), ZoneId.of("UTC"))
        JwtTokenUtil pastJwtTokenUtil = new JwtTokenUtil(
            new ClassPathResource("test-jwt-keystore.p12"),
            "Kalamaja123".toCharArray(),
            "PARTNER AS",
            "TULEVA",
            partnerPublicKey,
            partnerPublicKey,
            pastClock)

        String token = pastJwtTokenUtil.generateAccessToken(person, authorities)

    when:
        jwtTokenUtil.getPersonFromToken(token)

    then:
        thrown(ExpiredJwtException)
  }
}
