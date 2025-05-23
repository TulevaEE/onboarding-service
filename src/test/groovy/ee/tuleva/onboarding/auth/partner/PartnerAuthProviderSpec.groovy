package ee.tuleva.onboarding.auth.partner

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.principal.PersonImpl
import ee.tuleva.onboarding.auth.principal.PrincipalService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.UnsupportedJwtException
import spock.lang.Specification

import java.security.PublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static ee.tuleva.onboarding.auth.GrantType.*
import static ee.tuleva.onboarding.auth.KeyStoreFixture.getKeyPair
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.partner.PublicKeyTestHelper.publicKeyToResource
import static java.time.temporal.ChronoUnit.HOURS

class PartnerAuthProviderSpec extends Specification {

  PartnerPublicKeyConfiguration partnerPublicKeyConfiguration = new PartnerPublicKeyConfiguration()
  PublicKey partnerPublicKey1 = partnerPublicKeyConfiguration.partnerPublicKey1(publicKeyToResource(keyPair.public))
  PublicKey partnerPublicKey2 = partnerPublicKeyConfiguration.partnerPublicKey2(publicKeyToResource(keyPair.public))
  Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"))
  PrincipalService principalService = Mock()
  PartnerAuthProvider partnerAuthProvider = new PartnerAuthProvider(partnerPublicKey1, partnerPublicKey2, clock, principalService)

  def "support PARTNER grant type"() {
    expect:
    !partnerAuthProvider.supports(null)
    !partnerAuthProvider.supports(ID_CARD)
    !partnerAuthProvider.supports(SMART_ID)
    !partnerAuthProvider.supports(MOBILE_ID)
    partnerAuthProvider.supports(PARTNER)
  }

  def "can authenticate with partner handover token from smart-id"() {
    given:
    def sampleUserId = 123L

    def handoverToken = Jwts.builder()
        .subject(samplePerson.personalCode)
        .signWith(keyPair.private)
        .expiration(Date.from(clock.instant().plus(1, HOURS)))
        .claim("tokenType", "HANDOVER")
        .claim("firstName", samplePerson.firstName)
        .claim("lastName", samplePerson.lastName)
        .claim("iss", "testpartner")
        .claim("authenticationMethod", "SMART_ID")
        .claim("signingMethod", "SMART_ID")
        .claim("documentNumber", "PNOEE-30303039816-MOCK-Q")
        .compact()

    def attributes = [
        "grantType"                  : "PARTNER",
        "issuer"                     : "testpartner",
        "partnerAuthenticationMethod": "SMART_ID",
        "partnerSigningMethod"       : "SMART_ID",
        "documentNumber"             : "PNOEE-30303039816-MOCK-Q",
        "phoneNumber"                : null,
    ]

    def person = PersonImpl.builder()
        .personalCode(samplePerson.personalCode)
        .firstName(samplePerson.firstName)
        .lastName(samplePerson.lastName).build()

    def authenticatedPerson = AuthenticatedPerson.builder()
        .firstName(samplePerson.firstName)
        .lastName(samplePerson.lastName)
        .personalCode(samplePerson.personalCode)
        .userId(sampleUserId)
        .attributes(attributes)
        .build()

    1 * principalService.getFrom(*_) >> { Person prsn, Map<String, String> attrs ->
      assert prsn == person
      assert attrs == attributes
      return authenticatedPerson
    }

    when:
    def response = partnerAuthProvider.authenticate(handoverToken)

    then:
    response == authenticatedPerson
  }

  def "can authenticate with partner handover token from mobile-id"() {
    given:
    def sampleUserId = 123L

    def handoverToken = Jwts.builder()
        .subject(samplePerson.personalCode)
        .signWith(keyPair.private)
        .expiration(Date.from(clock.instant().plus(1, HOURS)))
        .claim("tokenType", "HANDOVER")
        .claim("firstName", samplePerson.firstName)
        .claim("lastName", samplePerson.lastName)
        .claim("iss", "testpartner")
        .claim("authenticationMethod", "MOBILE_ID")
        .claim("signingMethod", "MOBILE_ID")
        .claim("phoneNumber", "+37255555555")
        .compact()

    def attributes = [
        "grantType"                  : "PARTNER",
        "issuer"                     : "testpartner",
        "partnerAuthenticationMethod": "MOBILE_ID",
        "partnerSigningMethod"       : "MOBILE_ID",
        "phoneNumber"                : "+37255555555",
        "documentNumber"             : null,
    ]

    def person = PersonImpl.builder()
        .personalCode(samplePerson.personalCode)
        .firstName(samplePerson.firstName)
        .lastName(samplePerson.lastName).build()

    def authenticatedPerson = AuthenticatedPerson.builder()
        .firstName(samplePerson.firstName)
        .lastName(samplePerson.lastName)
        .personalCode(samplePerson.personalCode)
        .userId(sampleUserId)
        .attributes(attributes)
        .build()

    1 * principalService.getFrom(*_) >> { Person prsn, Map<String, String> attrs ->
      assert prsn == person
      assert attrs == attributes
      return authenticatedPerson
    }

    when:
    def response = partnerAuthProvider.authenticate(handoverToken)

    then:
    response == authenticatedPerson
  }

  def "validates token"() {
    given:
    def handoverToken = Jwts.builder()
        .signWith(keyPair.private)
        .claim("tokenType", "gibberish")
        .compact()

    when:
    partnerAuthProvider.authenticate(handoverToken)

    then:
    thrown(UnsupportedJwtException)
  }

}
