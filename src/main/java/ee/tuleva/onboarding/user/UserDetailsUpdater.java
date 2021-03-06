package ee.tuleva.onboarding.user;

import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserDetailsUpdater {

  private final UserService userService;
  private final ContactDetailsService contactDetailsService;

  @EventListener
  public void onBeforeTokenGrantedEvent(BeforeTokenGrantedEvent event) {
    Person person = event.getPerson();
    String firstName = capitalizeFully(person.getFirstName());
    String lastName = capitalizeFully(person.getLastName());

    log.info(
        "Updating user name: timestamp={}, name={} {}", event.getTimestamp(), firstName, lastName);

    userService
        .findByPersonalCode(person.getPersonalCode())
        .ifPresent(
            user -> {
              user.setFirstName(firstName);
              user.setLastName(lastName);
              userService.save(user);
            });
  }

  @EventListener
  public void onAfterTokenGrantedEvent(AfterTokenGrantedEvent event) {
    Person person = event.getPerson();
    String token = event.getAccessToken().getValue();

    userService
        .findByPersonalCode(person.getPersonalCode())
        .map(
            user -> {
              if (!user.hasContactDetails()) {
                log.info("User contact details missing. Filling them in with EPIS data");
                UserPreferences contactDetails =
                    contactDetailsService.getContactDetails(person, token);
                userService.updateUser(
                    person.getPersonalCode(),
                    StringUtils.trim(contactDetails.getEmail()),
                    StringUtils.trim(contactDetails.getPhoneNumber()));
              }
              return user;
            });
  }
}
