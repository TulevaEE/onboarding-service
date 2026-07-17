package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.event.RoleSwitchedEvent;
import ee.tuleva.onboarding.epis.EpisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OnRoleSwitchEpisCacheClearer {

  private final EpisService episService;

  @EventListener
  public void onRoleSwitched(RoleSwitchedEvent event) {
    var person = event.person();
    log.info(
        "Clearing EPIS cache on role switch: personalCode={}, representedPersonalCode={}",
        person.getPersonalCode(),
        person.getRepresentedPersonalCode());

    episService.clearCache(person);
  }
}
