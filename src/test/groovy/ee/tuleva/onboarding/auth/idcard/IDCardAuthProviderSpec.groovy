package ee.tuleva.onboarding.auth.idcard

import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.idcard.exception.IdCardSessionNotFoundException
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.idcard.IdCardSession.ID_DOCUMENT_TYPE_ATTRIBUTE
import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.DIGITAL_ID_CARD

class IDCardAuthProviderSpec extends Specification {
  private final GenericSessionStore genericSessionStore = Mock()
  private final PrincipalService principalService = Mock()
  private final IDCardAuthProvider idCardAuthProvider = new IDCardAuthProvider(
      genericSessionStore,
      principalService
  )

  def "supports id card"() {
    expect:
    idCardAuthProvider.supports(GrantType.ID_CARD)
    !idCardAuthProvider.supports(GrantType.MOBILE_ID)
    !idCardAuthProvider.supports(GrantType.SMART_ID)
  }

  def "throws when session is missing"() {
    given:
    String authenticationHash = "dummy"
    genericSessionStore.get(IdCardSession) >> Optional.empty()
    when:
    idCardAuthProvider.authenticate(authenticationHash)
    then:
    thrown(IdCardSessionNotFoundException)
  }

  def "returns person when login is complete"() {
    given:
    String authenticationHash = "dummy"
    AuthenticatedPerson person = sampleAuthenticatedPersonAndMember()
        .build()
    IdCardSession session = IdCardSession.builder()
        .documentType(DIGITAL_ID_CARD)
        .build()
    genericSessionStore.get(IdCardSession) >> Optional.of(session)
    principalService.getFrom(session, [(ID_DOCUMENT_TYPE_ATTRIBUTE): DIGITAL_ID_CARD.name()]) >> person
    when:
    AuthenticatedPerson result = idCardAuthProvider.authenticate(authenticationHash)
    then:
    result == person
  }
}
