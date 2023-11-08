package ee.tuleva.onboarding.auth.partner

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.PersonImpl
import ee.tuleva.onboarding.auth.principal.PrincipalService
import io.jsonwebtoken.Jwts
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static ee.tuleva.onboarding.auth.GrantType.*
import static ee.tuleva.onboarding.auth.KeyStoreFixture.*
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static java.time.temporal.ChronoUnit.HOURS

class PartnerAuthProviderSpec extends Specification {

  Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"))
  JwtTokenUtil jwtTokenUtil = new JwtTokenUtil(keyStore(), keyStorePassword, clock)
  PrincipalService principalService = Mock()

  PartnerAuthProvider partnerAuthProvider = new PartnerAuthProvider(jwtTokenUtil, principalService)

  def "support PARTNER grant type"() {
    expect:
    !partnerAuthProvider.supports(null)
    !partnerAuthProvider.supports(ID_CARD)
    !partnerAuthProvider.supports(SMART_ID)
    !partnerAuthProvider.supports(MOBILE_ID)
    partnerAuthProvider.supports(PARTNER)
  }

  def "can authenticate with partner handover token"() {
    given:
    def sampleUserId = 123L
    def handoverToken = Jwts.builder()
        .setSubject(samplePerson.personalCode)
        .signWith(keyPair.private)
        .setExpiration(Date.from(clock.instant().plus(1, HOURS)))
        .claim("firstName", samplePerson.firstName)
        .claim("lastName", samplePerson.lastName)
        .compact()
    principalService.getFrom(
        PersonImpl.builder()
            .personalCode(samplePerson.personalCode)
            .firstName(samplePerson.firstName)
            .lastName(samplePerson.lastName).build(), [:]) >> AuthenticatedPerson.builder()
        .firstName(samplePerson.firstName)
        .lastName(samplePerson.lastName)
        .personalCode(samplePerson.personalCode)
        .userId(sampleUserId)
        .attributes([:])
        .build()

    when:
    def authenticatedPerson = partnerAuthProvider.authenticate(handoverToken)

    then:
    authenticatedPerson == AuthenticatedPerson.builder()
        .firstName(samplePerson.firstName)
        .lastName(samplePerson.lastName)
        .personalCode(samplePerson.personalCode)
        .userId(sampleUserId)
        .attributes([:])
        .build()
  }
}
