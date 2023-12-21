package ee.tuleva.onboarding.auth.principal;

import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.Map;
import java.util.Optional;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrincipalService {

  private final UserService userService;

  public AuthenticatedPerson getFrom(@Valid Person person, Map<String, String> attributes) {

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
