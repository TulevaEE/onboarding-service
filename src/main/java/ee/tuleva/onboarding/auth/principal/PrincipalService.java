package ee.tuleva.onboarding.auth.principal;

import static ee.tuleva.onboarding.auth.principal.Names.formatted;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;

import ee.tuleva.onboarding.auth.role.Role;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@NullMarked
public class PrincipalService {

  private final PrincipalUsers principalUsers;

  public AuthenticatedPerson getFrom(@Valid Person person, Map<String, String> attributes) {
    return getFrom(
        person,
        attributes,
        new Role(PERSON, person.getPersonalCode(), formatted(person.getFullName())));
  }

  public AuthenticatedPerson getFrom(
      @Valid Person person, Map<String, String> attributes, @Nullable Role role) {

    var user = principalUsers.findOrCreate(person);

    if (!user.active()) {
      log.info("Failed to login inactive user with personal code {}", person.getPersonalCode());
      throw new IllegalStateException("INACTIVE_USER");
    }

    return AuthenticatedPerson.builder()
        .firstName(formatted(person.getFirstName()))
        .lastName(formatted(person.getLastName()))
        .personalCode(person.getPersonalCode())
        .userId(user.id())
        .attributes(attributes)
        .role(role)
        .build();
  }

  public AuthenticatedPerson withRole(AuthenticatedPerson person, Role role) {
    return person.toBuilder().role(role).build();
  }
}
