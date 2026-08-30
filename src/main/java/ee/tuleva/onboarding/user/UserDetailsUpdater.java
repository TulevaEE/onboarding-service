package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.auth.principal.Names.formatted;

import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.UserContacts.ContactSummary;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@NullMarked
public class UserDetailsUpdater {

  private static final int AFTER_AML_CHECKS = 2;

  private final UserService userService;
  private final UserContacts userContacts;

  @EventListener
  @Order(AFTER_AML_CHECKS)
  public void updateUserName(AfterTokenGrantedEvent event) {
    Person person = event.getPerson();

    log.info(
        "Updating user name: timestamp={}, personal code={}",
        event.getTimestamp(),
        person.getPersonalCode());

    userService
        .findByPersonalCode(person.getPersonalCode())
        .ifPresent(
            user -> {
              user.setFirstName(formatted(person.getFirstName()));
              user.setLastName(formatted(person.getLastName()));
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
      ContactSummary contactSummary = userContacts.forPerson(person, jwtToken);
      String phoneNumber = StringUtils.trim(contactSummary.phoneNumber());

      Optional<String> email =
          contactSummary.email() != null
              ? Optional.of(StringUtils.trim(contactSummary.email()))
              : Optional.empty();

      if (userService.isExistingEmail(person.getPersonalCode(), email)) {
        log.info(
            "User with given e-mail already exists, leaving the field empty for the user to fill: userId={}",
            user.getId());
        email = Optional.empty();
      }

      log.info("User contact details missing. Filling them in from the user's contact record");
      userService.updateUser(person.getPersonalCode(), email, phoneNumber);
    }
  }
}
