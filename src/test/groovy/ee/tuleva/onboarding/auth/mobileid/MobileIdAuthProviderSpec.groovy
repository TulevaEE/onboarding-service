package ee.tuleva.onboarding.auth.mobileid


import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.GrantType.*
import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER
import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError

class MobileIdAuthProviderSpec extends Specification {
  private final GenericSessionStore genericSessionStore = Mock()
  private final MobileIdAuthService mobileIdAuthService = Mock()
  private final PrincipalService principalService = Mock()
  private final RememberedMobileIdPhones rememberedPhones = Mock()
  private final MobileIdAuthProvider mobileIdAuthProvider = new MobileIdAuthProvider(
      genericSessionStore,
      mobileIdAuthService,
      principalService,
      rememberedPhones
  )

  def "supports mobileid"() {
    expect:
    mobileIdAuthProvider.supports(MOBILE_ID)
    !mobileIdAuthProvider.supports(SMART_ID)
    !mobileIdAuthProvider.supports(ID_CARD)
  }

  def "throws when session is missing"() {
    given:
    String authenticationHash = "dummy"
    genericSessionStore.get(MobileIDSession) >> Optional.empty()
    when:
    mobileIdAuthProvider.authenticate(authenticationHash)
    then:
    thrown(MobileIdSessionNotFoundException)
  }

  def "throws when login is not complete"() {
    given:
    String authenticationHash = "dummy"
    MobileIDSession session = MobileIdFixture.sampleMobileIdSession
    mobileIdAuthService.isLoginComplete(session) >> false
    genericSessionStore.get(MobileIDSession) >> Optional.of(session)
    when:
    mobileIdAuthProvider.authenticate(authenticationHash)
    then:
    thrown(AuthNotCompleteException)
  }

  def "returns person when login is complete"() {
    given:
    String authenticationHash = "dummy"
    AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build()
    MobileIDSession session = MobileIdFixture.sampleMobileIdSession
    mobileIdAuthService.isLoginComplete(session) >> true
    genericSessionStore.get(MobileIDSession) >> Optional.of(session)
    principalService.getFrom(session, [
        (PHONE_NUMBER): session.phoneNumber,
        (GRANT_TYPE)  : MOBILE_ID.name()
    ]) >> person
    when:
    AuthenticatedPerson result = mobileIdAuthProvider.authenticate(authenticationHash)
    then:
    result == person
    1 * rememberedPhones.remember(person.personalCode, session.phoneNumber)
  }

  def "remembers nothing while the login is not complete"() {
    given:
    MobileIDSession session = MobileIdFixture.sampleMobileIdSession
    mobileIdAuthService.isLoginComplete(session) >> false
    genericSessionStore.get(MobileIDSession) >> Optional.of(session)
    when:
    mobileIdAuthProvider.authenticate(null)
    then:
    thrown(AuthNotCompleteException)
    0 * rememberedPhones.remember(_, _)
  }

  def "forgets a remembered phone Mobile-ID says does not belong to the person and asks for the phone"() {
    given:
    MobileIDSession session = new MobileIDSession("12345", "challenge", MobileIdFixture.hash, "+37255555555")
    session.rememberedPhoneId = 3L
    genericSessionStore.get(MobileIDSession) >> Optional.of(session)
    mobileIdAuthService.isLoginComplete(session) >> { throw new MobileIdNotMidClientException() }
    when:
    mobileIdAuthProvider.authenticate(null)
    then:
    def exception = thrown(MobileIdException)
    exception.errorsResponse.errors*.code == ["mobile.id.phone.number.required"]
    1 * rememberedPhones.forget(3L)
  }

  def "a typed phone Mobile-ID rejects leaves what is remembered alone"() {
    given:
    MobileIDSession session = new MobileIDSession("12345", "challenge", MobileIdFixture.hash, "+37251234567")
    genericSessionStore.get(MobileIDSession) >> Optional.of(session)
    mobileIdAuthService.isLoginComplete(session) >> { throw new MobileIdNotMidClientException() }
    when:
    mobileIdAuthProvider.authenticate(null)
    then:
    def exception = thrown(MobileIdException)
    exception.errorsResponse.errors*.code == ["mobile.id.certificates.revoked"]
    0 * rememberedPhones.forget(_)
  }

  def "a login from a remembered phone releases this browser before the remembered phone renews its cookie"() {
    given:
    MobileIDSession session = sessionFromRememberedPhone()
    AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build()
    genericSessionStore.get(MobileIDSession) >> Optional.of(session)
    mobileIdAuthService.isLoginComplete(session) >> true
    principalService.getFrom(session, _) >> person
    when:
    mobileIdAuthProvider.authenticate(null)
    then:
    1 * rememberedPhones.releaseLoginStart()
    then:
    1 * rememberedPhones.remember(person.personalCode, session.phoneNumber)
  }

  def "a login from a remembered phone that Mobile-ID ends with #failure.class.simpleName releases this browser"() {
    given:
    MobileIDSession session = sessionFromRememberedPhone()
    genericSessionStore.get(MobileIDSession) >> Optional.of(session)
    mobileIdAuthService.isLoginComplete(session) >> { throw failure }
    when:
    mobileIdAuthProvider.authenticate(null)
    then:
    thrown(RuntimeException)
    1 * rememberedPhones.releaseLoginStart()
    where:
    failure << [
        new MobileIdException(ofSingleError("mobile.id.cancelled", "Cancelled on the phone.")),
        new MobileIdNotMidClientException(),
        new IllegalStateException("Mobile-ID answered with something unexpected")
    ]
  }

  def "a login from a remembered phone still running keeps this browser claimed"() {
    given:
    MobileIDSession session = sessionFromRememberedPhone()
    genericSessionStore.get(MobileIDSession) >> Optional.of(session)
    mobileIdAuthService.isLoginComplete(session) >> false
    when:
    mobileIdAuthProvider.authenticate(null)
    then:
    thrown(AuthNotCompleteException)
    0 * rememberedPhones.releaseLoginStart()
  }

  def "a login from a typed phone leaves the claim of this browser alone"() {
    given:
    MobileIDSession session = new MobileIDSession("12345", "challenge", MobileIdFixture.hash, "+37251234567")
    genericSessionStore.get(MobileIDSession) >> Optional.of(session)
    mobileIdAuthService.isLoginComplete(session) >> true
    principalService.getFrom(session, _) >> sampleAuthenticatedPersonAndMember().build()
    when:
    mobileIdAuthProvider.authenticate(null)
    then:
    0 * rememberedPhones.releaseLoginStart()
  }

  private static MobileIDSession sessionFromRememberedPhone() {
    MobileIDSession session = new MobileIDSession("12345", "challenge", MobileIdFixture.hash, "+37255555555")
    session.rememberedPhoneId = 3L
    return session
  }
}
