package ee.tuleva.onboarding.auth.principal;

import static ee.tuleva.onboarding.auth.principal.Person.capitalize;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;

import ee.tuleva.onboarding.auth.role.Role;
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
    return getFrom(
        person,
        attributes,
        new Role(PERSON, person.getPersonalCode(), capitalize(person.getFullName())));
  }

  public AuthenticatedPerson getFrom(
      @Valid Person person, Map<String, String> attributes, Role role) {

    Optional<User> userOptional = userService.findByPersonalCode(person.getPersonalCode());

    User user = userOptional.orElseGet(() -> createUser(person));

    if (!user.getActive()) {
      log.info("Failed to login inactive user with personal code {}", person.getPersonalCode());
      throw new IllegalStateException("INACTIVE_USER");
    }

    return AuthenticatedPerson.builder()
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .personalCode(person.getPersonalCode())
        .userId(user.getId())
        .attributes(attributes)
        .role(role)
        .build();
  }

  public AuthenticatedPerson withRole(AuthenticatedPerson person, Role role) {
    return AuthenticatedPerson.builder()
        .personalCode(person.getPersonalCode())
        .firstName(person.getFirstName())
        .lastName(person.getLastName())
        .userId(person.getUserId())
        .attributes(person.getAttributes())
        .role(role)
        .build();
  }

  private User createUser(Person person) {
    return userService.createNewUser(
        User.builder()
            .firstName(capitalize(person.getFirstName()))
            .lastName(capitalize(person.getLastName()))
            .personalCode(person.getPersonalCode())
            .active(true)
            .build());
  }
}
