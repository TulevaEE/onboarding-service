package ee.tuleva.onboarding.auth.smartid

import ee.sk.smartid.AuthenticationIdentity
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.GrantType.GRANT_TYPE
import static ee.tuleva.onboarding.auth.GrantType.SMART_ID

class SmartIdAuthProviderSpec extends Specification {
  private final SmartIdAuthService smartIdAuthService = Mock()
  private final PrincipalService principalService = Mock()
  private final SmartIdAuthProvider smartIdAuthProvider = new SmartIdAuthProvider(
      smartIdAuthService,
      principalService
  )

  def "supports smartid"() {
    expect:
    smartIdAuthProvider.supports(SMART_ID)
    !smartIdAuthProvider.supports(GrantType.ID_CARD)
    !smartIdAuthProvider.supports(GrantType.MOBILE_ID)
  }

  def "throws when hash is missing"() {
    when:
    smartIdAuthProvider.authenticate(null)
    then:
    thrown(SmartIdSessionNotFoundException)
  }

  def "throws when identity is missing"() {
    given:
    String authenticationHash = "dummy"
    smartIdAuthService.getAuthenticationIdentity(authenticationHash) >> Optional.empty()
    when:
    smartIdAuthProvider.authenticate(authenticationHash)
    then:
    thrown(AuthNotCompleteException)
  }

  def "returns person when identity is present"() {
    given:
    AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build()
    String authenticationHash = "dummy"
    smartIdAuthService.getAuthenticationIdentity(authenticationHash) >> Optional.of(new AuthenticationIdentity())
    principalService.getFrom(_ as Person, [
        (GRANT_TYPE): SMART_ID.name()
    ]) >> person
    when:
    AuthenticatedPerson result = smartIdAuthProvider.authenticate(authenticationHash)
    then:
    result == person
  }
}
