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

import static java.time.temporal.ChronoUnit.HOURS

class JwtTokenUtilSpec extends Specification {

  PartnerPublicKeyConfiguration partnerPublicKeyConfiguration = new PartnerPublicKeyConfiguration()
  PublicKey partnerPublicKey =
      partnerPublicKeyConfiguration.partnerPublicKey1(new ClassPathResource("test-partner-public-key.pem"))
  private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"))

  private final JwtTokenUtil jwtTokenUtil = new JwtTokenUtil(
      new ClassPathResource("test-jwt-keystore.p12"),
      "Kalamaja123".toCharArray(),
      partnerPublicKey,
      partnerPublicKey,
      clock)

  def "generates service token"() {
    when:
    String token = jwtTokenUtil.generateServiceToken()
    then:
    token == "eyJhbGciOiJSUzI1NiJ9.eyJhdXRob3JpdGllcyI6W10sInN1YiI6Im9uYm9hcmRpbmctc2VydmljZSIsImlhdCI6MCwiZXhwIjoxODAwfQ.IARX6oWn8f2g0l7i5gFLMNGMmkeFgtkfVNSO1OGGfWYiAjYbPPrRBWwagzKMZfx1tx9jac3O0vpjMpti69Yb42Rnjasflv5onEdRr2chYqX34UKP8ag1CvdoLQaZ1WG2BT7v5dqVcmQnMLByjV3a8uZeMjvsQpCBESlQyUQQWiLOecjBe2F1uaChxGyiHDDFzYzGMRTCuqs8bqZBEjDlrTqMDXXZZSDf0Xe0_yfWeS_beUe0O5wIODf8xJkV5mkK73yeLNMvkbc_qt9y0KFOIytr2n9iDczC2bJsHEgdM5Y0lyov2BlSvwycnQ5jjFTiFBPlquaKv2MWQBY1QnaIeQ"
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
    then:
    parsed.personalCode == "38812121215"
    parsed.firstName == "Peeter"
    parsed.lastName == "Meeter"
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
        tokenType == TokenType.REFRESH
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
        List<String> extractedAuthorities = jwtTokenUtil.getAuthoritiesFromToken(token)

    then:
        attributes.get("email") == "peeter@meeter.com"
        extractedAuthorities.contains("USER")
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
