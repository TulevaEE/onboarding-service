package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.auth.principal.Names.formatted;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PrincipalUsers;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PrincipalUsersAdapter implements PrincipalUsers {

  private final UserService userService;

  @Override
  public PrincipalUser findOrCreate(Person person) {
    User user =
        userService
            .findByPersonalCode(person.getPersonalCode())
            .orElseGet(() -> createUser(person));
    Long userId = requireNonNull(user.getId(), "User id missing for authenticated person");
    return new PrincipalUser(userId, user.getActive());
  }

  @Override
  public boolean isMember(Long userId) {
    return userService.getById(userId).orElseThrow().getMember().isPresent();
  }

  @Override
  public Optional<String> fullName(String personalCode) {
    return userService.findByPersonalCode(personalCode).map(User::getFullName);
  }

  private User createUser(Person person) {
    return userService.createNewUser(
        User.builder()
            .firstName(formatted(person.getFirstName()))
            .lastName(formatted(person.getLastName()))
            .personalCode(person.getPersonalCode())
            .active(true)
            .build());
  }
}
