package ee.tuleva.onboarding.auth.principal;

import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrincipalService {

  private final UserService userService;

  public AuthenticatedPerson getFrom(@Valid Person person, Map<String, String> attributes) {
    return getFrom(person, attributes, new ActingAs.Person(person.getPersonalCode()));
  }

  public AuthenticatedPerson getFrom(
      @Valid Person person, Map<String, String> attributes, ActingAs actingAs) {

    Optional<User> userOptional = userService.findByPersonalCode(person.getPersonalCode());

    User user = userOptional.orElseGet(() -> createUser(person));

    if (!user.getActive()) {
      log.info("Failed to login inactive user with personal code {}", person.getPersonalCode());
      throw new IllegalStateException("INACTIVE_USER");
    }

    return AuthenticatedPerson.builder()
        .firstName(person.getFirstName())
        .lastName(person.getLastName())
        .personalCode(person.getPersonalCode())
        .userId(user.getId())
        .attributes(attributes)
        .actingAs(actingAs)
        .build();
  }

  public AuthenticatedPerson withActingAs(AuthenticatedPerson person, ActingAs actingAs) {
    return AuthenticatedPerson.builder()
        .personalCode(person.getPersonalCode())
        .firstName(person.getFirstName())
        .lastName(person.getLastName())
        .userId(person.getUserId())
        .attributes(person.getAttributes())
        .actingAs(actingAs)
        .build();
  }

  private User createUser(Person person) {
    return userService.createNewUser(
        User.builder()
            .firstName(capitalizeFully(person.getFirstName(), ' ', '-'))
            .lastName(capitalizeFully(person.getLastName(), ' ', '-'))
            .personalCode(person.getPersonalCode())
            .active(true)
            .build());
  }
}
