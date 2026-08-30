package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OnLoginAccountStatementCacheClearer {

  private final EpisService episService;

  @EventListener
  public void onAfterTokenGrantedEvent(AfterTokenGrantedEvent event) {
    Person person = event.getPerson();
    log.info(
        "On AfterTokenGrantedEvent: timestamp={}, personal code={}",
        event.getTimestamp(),
        person.getPersonalCode());

    episService.clearCache(person);
  }
}
