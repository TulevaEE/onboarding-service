package ee.tuleva.onboarding.auth.smartid

import ee.sk.smartid.AuthenticationHash
import ee.sk.smartid.AuthenticationIdentity
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import ee.tuleva.onboarding.time.MutableClock
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.GrantType.GRANT_TYPE
import static ee.tuleva.onboarding.auth.GrantType.SMART_ID
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.firstName
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.lastName
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.personalCode

class SmartIdAuthProviderSpec extends Specification {
  private final GenericSessionStore genericSessionStore = Mock()
  private final PrincipalService principalService = Mock()
  private final MutableClock clock = new MutableClock()
  private final SmartIdAuthProvider smartIdAuthProvider = new SmartIdAuthProvider(
      genericSessionStore,
      principalService,
      clock
  )

  def "supports smartid"() {
    expect:
    smartIdAuthProvider.supports(SMART_ID)
    !smartIdAuthProvider.supports(GrantType.ID_CARD)
    !smartIdAuthProvider.supports(GrantType.MOBILE_ID)
  }

  def "throws when no SmartIdSession is in HttpSession"() {
    given:
    genericSessionStore.get(SmartIdSession) >> Optional.empty()
    when:
    smartIdAuthProvider.authenticate("any-hash")
    then:
    thrown(SmartIdSessionNotFoundException)
  }

  def "throws when the polled hash does not match the session hash"() {
    given:
    SmartIdSession session = sessionWithHash()
    genericSessionStore.get(SmartIdSession) >> Optional.of(session)
    when:
    smartIdAuthProvider.authenticate("different-hash")
    then:
    thrown(SmartIdSessionNotFoundException)
  }

  def "throws SmartIdException when the session has an error recorded"() {
    given:
    SmartIdSession session = sessionWithHash()
    session.errorCode = "smart.id.user.refused"
    session.errorMessage = "Smart ID User refused"
    genericSessionStore.get(SmartIdSession) >> Optional.of(session)
    when:
    smartIdAuthProvider.authenticate(session.authenticationHash.hashInBase64)
    then:
    SmartIdException e = thrown()
    e.errorsResponse.errors[0].code == "smart.id.user.refused"
  }

  def "throws AuthNotCompleteException when person is not yet recorded"() {
    given:
    SmartIdSession session = sessionWithHash()
    genericSessionStore.get(SmartIdSession) >> Optional.of(session)
    when:
    smartIdAuthProvider.authenticate(session.authenticationHash.hashInBase64)
    then:
    thrown(AuthNotCompleteException)
  }

  def "throws when grant is older than 120 seconds (replay protection)"() {
    given:
    SmartIdSession session = sessionWithHash()
    AuthenticationIdentity identity = new AuthenticationIdentity()
    identity.identityCode = personalCode
    identity.givenName = firstName
    identity.surname = lastName
    identity.country = "EE"
    session.person = new SmartIdPerson(identity)
    genericSessionStore.get(SmartIdSession) >> Optional.of(session)
    clock.tick(121, java.time.temporal.ChronoUnit.SECONDS)
    when:
    smartIdAuthProvider.authenticate(session.authenticationHash.hashInBase64)
    then:
    thrown(SmartIdSessionNotFoundException)
  }

  def "returns the authenticated person when polled hash matches and person is set"() {
    given:
    AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build()
    SmartIdSession session = sessionWithHash()
    AuthenticationIdentity identity = new AuthenticationIdentity()
    identity.identityCode = personalCode
    identity.givenName = firstName
    identity.surname = lastName
    identity.country = "EE"
    session.person = new SmartIdPerson(identity)
    genericSessionStore.get(SmartIdSession) >> Optional.of(session)
    principalService.getFrom(session.person, [(GRANT_TYPE): SMART_ID.name()]) >> person
    when:
    AuthenticatedPerson result = smartIdAuthProvider.authenticate(session.authenticationHash.hashInBase64)
    then:
    result == person
  }

  private SmartIdSession sessionWithHash() {
    return new SmartIdSession("12345", personalCode, AuthenticationHash.generateRandomHash(), Instant.now(clock))
  }
}
