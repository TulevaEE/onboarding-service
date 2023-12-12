package ee.tuleva.onboarding.auth.mobileid


import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.GrantType.*
import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER

class MobileIdAuthProviderSpec extends Specification {
  private final GenericSessionStore genericSessionStore = Mock()
  private final MobileIdAuthService mobileIdAuthService = Mock()
  private final PrincipalService principalService = Mock()
  private final MobileIdAuthProvider mobileIdAuthProvider = new MobileIdAuthProvider(
      genericSessionStore,
      mobileIdAuthService,
      principalService
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
  }
}
