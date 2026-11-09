package ee.tuleva.onboarding.auth.partner

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.principal.PersonImpl
import ee.tuleva.onboarding.auth.principal.PrincipalService
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import spock.lang.Specification

import java.security.PrivateKey
import java.security.PublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static ee.tuleva.onboarding.auth.GrantType.*
import static ee.tuleva.onboarding.auth.KeyStoreFixture.getKeyPair
import static ee.tuleva.onboarding.auth.KeyStoreFixture.getPartnerKeyPair
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.partner.PublicKeyTestHelper.publicKeyToResource
import static java.time.temporal.ChronoUnit.HOURS

class PartnerAuthProviderSpec extends Specification {

  static final String ISSUER = "COOP PANK AS"
  static final String CLIENT_ID = "TULEVA"
  static final String TOKEN_ID = "2716758c-970f-479c-9e61-52ad3692faef"

  PartnerPublicKeyConfiguration partnerPublicKeyConfiguration = new PartnerPublicKeyConfiguration()
  PublicKey partnerPublicKey1 = partnerPublicKeyConfiguration.partnerPublicKey1(publicKeyToResource(keyPair.public))
  PublicKey partnerPublicKey2 = partnerPublicKeyConfiguration.partnerPublicKey2(publicKeyToResource(keyPair.public))
  Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"))
  PrincipalService principalService = Mock()
  HandoverTokenUses handoverTokenUses = Mock()
  PartnerAuthProvider partnerAuthProvider = new PartnerAuthProvider(
      partnerPublicKey1, partnerPublicKey2, ISSUER, CLIENT_ID, clock, principalService, handoverTokenUses)

  Person person = PersonImpl.builder()
      .personalCode(samplePerson.personalCode)
      .firstName(samplePerson.firstName)
      .lastName(samplePerson.lastName).build()

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
    def handoverToken = handoverToken([
        authenticationMethod: "SMART_ID",
        signingMethod       : "SMART_ID",
        documentNumber      : "PNOEE-30303039816-MOCK-Q",
    ])
    def attributes = [
        "grantType"                  : "PARTNER",
        "issuer"                     : ISSUER,
        "partnerAuthenticationMethod": "SMART_ID",
        "partnerSigningMethod"       : "SMART_ID",
        "documentNumber"             : "PNOEE-30303039816-MOCK-Q",
        "phoneNumber"                : null,
    ]
    def authenticatedPerson = authenticatedPersonWith(attributes)
    1 * handoverTokenUses.firstUse(ISSUER, TOKEN_ID) >> true
    1 * principalService.getFrom(person, attributes) >> authenticatedPerson

    when:
    def response = partnerAuthProvider.authenticate(handoverToken)

    then:
    response == authenticatedPerson
  }

  def "can authenticate with partner handover token from mobile-id"() {
    given:
    def handoverToken = handoverToken([
        authenticationMethod: "MOBILE_ID",
        signingMethod       : "MOBILE_ID",
        phoneNumber         : "+37255555555",
    ])
    def attributes = [
        "grantType"                  : "PARTNER",
        "issuer"                     : ISSUER,
        "partnerAuthenticationMethod": "MOBILE_ID",
        "partnerSigningMethod"       : "MOBILE_ID",
        "phoneNumber"                : "+37255555555",
        "documentNumber"             : null,
    ]
    def authenticatedPerson = authenticatedPersonWith(attributes)
    1 * handoverTokenUses.firstUse(ISSUER, TOKEN_ID) >> true
    1 * principalService.getFrom(person, attributes) >> authenticatedPerson

    when:
    def response = partnerAuthProvider.authenticate(handoverToken)

    then:
    response == authenticatedPerson
  }

  def "falls back to the second partner public key when the first one does not verify the signature"() {
    given:
    def providerWithDistinctPartnerKeys = new PartnerAuthProvider(
        partnerPublicKey1, partnerKeyPair.public, ISSUER, CLIENT_ID, clock, principalService, handoverTokenUses)
    def handoverToken = handoverToken([:], partnerKeyPair.private)
    def authenticatedPerson = authenticatedPersonWith([:])
    1 * handoverTokenUses.firstUse(ISSUER, TOKEN_ID) >> true
    1 * principalService.getFrom(person, _) >> authenticatedPerson

    when:
    def response = providerWithDistinctPartnerKeys.authenticate(handoverToken)

    then:
    response == authenticatedPerson
  }

  def "accepts the token type in any letter case"() {
    given:
    1 * handoverTokenUses.firstUse(ISSUER, TOKEN_ID) >> true
    1 * principalService.getFrom(person, _) >> authenticatedPersonWith([:])

    when:
    partnerAuthProvider.authenticate(handoverToken([tokenType: "handover"]))

    then:
    noExceptionThrown()
  }

  def "refuses a handover token that is not a handover token"() {
    when:
    partnerAuthProvider.authenticate(handoverToken([tokenType: "ACCESS"]))

    then:
    thrown(HandoverTokenRejectedException)
    0 * principalService.getFrom(*_)
  }

  def "refuses a handover token the partner issued for another client"() {
    when:
    partnerAuthProvider.authenticate(handoverToken([cid: clientId]))

    then:
    thrown(HandoverTokenRejectedException)
    0 * handoverTokenUses.firstUse(*_)
    0 * principalService.getFrom(*_)

    where:
    clientId << ["ANOTHER PARTNER", null]
  }

  def "refuses a handover token from another issuer"() {
    when:
    partnerAuthProvider.authenticate(handoverToken([iss: issuer]))

    then:
    thrown(HandoverTokenRejectedException)
    0 * handoverTokenUses.firstUse(*_)
    0 * principalService.getFrom(*_)

    where:
    issuer << ["ANOTHER BANK AS", null]
  }

  def "refuses a handover token without an expiry"() {
    when:
    partnerAuthProvider.authenticate(handoverToken([exp: null]))

    then:
    thrown(HandoverTokenRejectedException)
    0 * handoverTokenUses.firstUse(*_)
    0 * principalService.getFrom(*_)
  }

  def "refuses a handover token without a token id"() {
    when:
    partnerAuthProvider.authenticate(handoverToken([jti: null]))

    then:
    thrown(HandoverTokenRejectedException)
    0 * handoverTokenUses.firstUse(*_)
    0 * principalService.getFrom(*_)
  }

  def "refuses a handover token that has already been used"() {
    given:
    1 * handoverTokenUses.firstUse(ISSUER, TOKEN_ID) >> false

    when:
    partnerAuthProvider.authenticate(handoverToken([:]))

    then:
    thrown(HandoverTokenRejectedException)
    0 * principalService.getFrom(*_)
  }

  def "refuses a handover token signed with another key as unverifiable"() {
    when:
    partnerAuthProvider.authenticate(handoverToken([:], partnerKeyPair.private))

    then:
    thrown(UnverifiableHandoverTokenException)
    0 * handoverTokenUses.firstUse(*_)
  }

  def "leaves an expired handover token to the expired token answer"() {
    when:
    partnerAuthProvider.authenticate(handoverToken([exp: Date.from(clock.instant().minusSeconds(1))]))

    then:
    thrown(ExpiredJwtException)
    0 * handoverTokenUses.firstUse(*_)
  }

  def "leaves a handover token that expired under the second partner key to the expired token answer"() {
    given:
    def providerWithDistinctPartnerKeys = new PartnerAuthProvider(
        partnerPublicKey1, partnerKeyPair.public, ISSUER, CLIENT_ID, clock, principalService, handoverTokenUses)

    when:
    providerWithDistinctPartnerKeys.authenticate(
        handoverToken([exp: Date.from(clock.instant().minusSeconds(1))], partnerKeyPair.private))

    then:
    thrown(ExpiredJwtException)
    0 * handoverTokenUses.firstUse(*_)
  }

  private String handoverToken(Map<String, Object> claimOverrides, PrivateKey signingKey = keyPair.private) {
    Map<String, Object> claims = [
        tokenType           : "HANDOVER",
        iss                 : ISSUER,
        cid                 : CLIENT_ID,
        jti                 : TOKEN_ID,
        firstName           : samplePerson.firstName,
        lastName            : samplePerson.lastName,
        authenticationMethod: "SMART_ID",
        signingMethod       : "SMART_ID",
        exp                 : Date.from(clock.instant().plus(1, HOURS)),
    ] + claimOverrides
    def builder = Jwts.builder()
        .subject(samplePerson.personalCode)
        .signWith(signingKey)
    def expiry = claims.remove("exp") as Date
    if (expiry != null) {
      builder.expiration(expiry)
    }
    claims.findAll { it.value != null }.each { builder.claim(it.key, it.value) }
    return builder.compact()
  }

  private static AuthenticatedPerson authenticatedPersonWith(Map<String, String> attributes) {
    return AuthenticatedPerson.builder()
        .firstName(samplePerson.firstName)
        .lastName(samplePerson.lastName)
        .personalCode(samplePerson.personalCode)
        .userId(123L)
        .attributes(attributes)
        .build()
  }
}
