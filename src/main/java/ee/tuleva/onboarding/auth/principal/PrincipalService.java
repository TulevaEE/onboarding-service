package ee.tuleva.onboarding.auth.principal;

import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrincipalService {

  private final UserService userService;

  public AuthenticatedPerson getFrom(Person person, Optional<String> phoneNumber) {

    Optional<User> userOptional = userService.findByPersonalCode(person.getPersonalCode());

    User user = userOptional.orElseGet(() -> createUser(person, phoneNumber));

    return getFromUser(user);
  }

  public AuthenticatedPerson getFrom(String personalCode) {
    Optional<User> userOptional = userService.findByPersonalCode(personalCode);
    return getFromUser(
        userOptional.orElseThrow(() -> new IllegalStateException("User not present!")));
  }

  private AuthenticatedPerson getFromUser(User user) {
    if (!user.getActive()) {
      log.info("Failed to login inactive user with personal code {}", user.getPersonalCode());
      throw new OAuth2AuthenticationException("INACTIVE_USER");
    }

    return AuthenticatedPerson.builder()
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .personalCode(user.getPersonalCode())
        .phoneNumber(user.getPhoneNumber())
        .userId(user.getId())
        .build();
  }

  private User createUser(Person person, Optional<String> phoneNumber) {
    return userService.createNewUser(
        User.builder()
            .firstName(capitalizeFully(person.getFirstName(), ' ', '-'))
            .lastName(capitalizeFully(person.getLastName(), ' ', '-'))
            .personalCode(person.getPersonalCode())
            .phoneNumber(phoneNumber.orElse(null))
            .active(true)
            .build());
  }
}
