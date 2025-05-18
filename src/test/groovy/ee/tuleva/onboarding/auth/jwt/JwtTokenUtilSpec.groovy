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

import static ee.tuleva.onboarding.auth.jwt.TokenType.ACCESS
import static ee.tuleva.onboarding.auth.jwt.TokenType.REFRESH
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
    token == "eyJhbGciOiJSUzI1NiJ9.eyJhdXRob3JpdGllcyI6WyJTRVJWSUNFIl0sInRva2VuVHlwZSI6IkFDQ0VTUyIsInN1YiI6Im9uYm9hcmRpbmctc2VydmljZSIsImlhdCI6MCwiZXhwIjoxODAwfQ.m7zozhdt1z89GV7kCqtpZ-fvUItQ49w1frNTGcBujmP4P00SstUqASyH8C58lCT63cIYd5W79FYBXmX5mwMn9QBM14stdCtpFEa96WPVdQlJwbp5MOKm8-jY6anmW9I1Dz7NyIoGfLYhZktJ-dumc5qTNbfl3TTQBssl75wx-cISIu9ne4PrHqd9ZKv9kQBeC9xgK6sAiXqVc4rpArQspP8dhkvPn47c4XU-Cr_ePg8nXinZU_x3NAaiLQ4z90qyYdDlku9f1NA0CwsXIbqGCTnIlZuhO1Az_xZDr7u0qGTh2pDe_b3Ss_bzEa6m1uKL0YLHuh935W2lTW8Ww6mGcQ"
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
