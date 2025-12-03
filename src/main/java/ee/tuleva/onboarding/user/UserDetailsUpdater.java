package ee.tuleva.onboarding.user;

import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import java.util.Optional;
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

    log.info("Updating user name: personalCode={}", person.getPersonalCode());

    userService
        .findByPersonalCode(person.getPersonalCode())
        .ifPresent(
            user -> {
              user.setFirstName(capitalizeFully(person.getFirstName(), ' ', '-'));
              user.setLastName(capitalizeFully(person.getLastName(), ' ', '-'));
              userService.save(user);
            });
  }

  @EventListener
  public void onAfterTokenGrantedEvent(AfterTokenGrantedEvent event) {
    Person person = event.getPerson();
    String accessToken = event.getAccessToken();

    userService
        .findByPersonalCode(person.getPersonalCode())
        .ifPresent(user -> updateContactDetails(person, accessToken, user));
  }

  private void updateContactDetails(Person person, String jwtToken, User user) {
    if (!user.hasContactDetails()) {
      ContactDetails contactDetails = contactDetailsService.getContactDetails(person, jwtToken);
      String phoneNumber = StringUtils.trim(contactDetails.getPhoneNumber());

      Optional<String> email =
          contactDetails.getEmail() != null
              ? Optional.of(StringUtils.trim(contactDetails.getEmail()))
              : Optional.empty();

      if (userService.isExistingEmail(person.getPersonalCode(), email)) {
        log.info(
            "User with given e-mail already exists, leaving the field empty for the user to fill: userId={}",
            user.getId());
        email = Optional.empty();
      }

      log.info("User contact details missing. Filling them in with EPIS data");
      userService.updateUser(person.getPersonalCode(), email, phoneNumber);
    }
  }
}
