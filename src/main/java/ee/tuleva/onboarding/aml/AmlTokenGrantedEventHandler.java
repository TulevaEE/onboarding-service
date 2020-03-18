package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmlTokenGrantedEventHandler {

  private final AmlService amlService;
  private final UserService userService;

  @EventListener
  public void onBeforeTokenGrantedEvent(BeforeTokenGrantedEvent event) {
    Person person = (Person) event.getAuthentication().getPrincipal();
    User user =
        userService
            .findByPersonalCode(person.getPersonalCode())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "User not found with code " + person.getPersonalCode()));
    amlService.checkUserAfterLogin(user, person);
  }
}
