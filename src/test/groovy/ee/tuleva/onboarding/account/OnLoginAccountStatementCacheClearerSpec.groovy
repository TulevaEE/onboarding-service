package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.AuthenticationTokens
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.epis.EpisService
import spock.lang.Specification

class OnLoginAccountStatementCacheClearerSpec extends Specification {

  EpisService episService = Mock(EpisService)
  OnLoginAccountStatementCacheClearer service =
      new OnLoginAccountStatementCacheClearer(episService)

  def "OnAfterTokenGrantedEvent: Starts clearing cache on event"() {
    given:

    AuthenticatedPerson samplePerson = AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember().build()

    AfterTokenGrantedEvent afterTokenGrantedEvent = new AfterTokenGrantedEvent(this, samplePerson, GrantType.ID_CARD, new AuthenticationTokens("access token", "refresh token"))

    when:
    service.onAfterTokenGrantedEvent(afterTokenGrantedEvent)

    then:
    1 * episService.clearCache(samplePerson)
  }
}
