package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.auth.AuthenticationAttributes
import ee.tuleva.onboarding.auth.GrantType
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.epis.EpisService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class OnLoginAccountStatementCacheClearerSpec extends Specification {

  EpisService episService = Mock(EpisService)
  OnLoginAccountStatementCacheClearer service =
      new OnLoginAccountStatementCacheClearer(episService)

  def "OnBeforeTokenGrantedEvent: Starts clearing cache on event"() {
    given:

    Person samplePerson = samplePerson()

    BeforeTokenGrantedEvent beforeTokenGrantedEvent = new BeforeTokenGrantedEvent(this, samplePerson, new AuthenticationAttributes(), GrantType.ID_CARD)

    when:
    service.onBeforeTokenGrantedEvent(beforeTokenGrantedEvent)

    then:
    1 * episService.clearCache(samplePerson)
  }
}
