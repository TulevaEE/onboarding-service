package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.epis.EpisService
import spock.lang.Specification

class OnLoginAccountStatementCacheClearerSpec extends Specification {

  EpisService episService = Mock(EpisService)
  OnLoginAccountStatementCacheClearer service =
      new OnLoginAccountStatementCacheClearer(episService)

  def "OnBeforeTokenGrantedEvent: Starts clearing cache on event"() {
    given:

    AuthenticatedPerson samplePerson = AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember().build()

    BeforeTokenGrantedEvent beforeTokenGrantedEvent = new BeforeTokenGrantedEvent(samplePerson, GrantType.ID_CARD)

    when:
    service.onBeforeTokenGrantedEvent(beforeTokenGrantedEvent)

    then:
    1 * episService.clearCache(samplePerson)
  }
}
