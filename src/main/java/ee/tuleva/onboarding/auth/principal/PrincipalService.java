package ee.tuleva.onboarding.auth.principal;

import static ee.tuleva.onboarding.auth.principal.Names.formatted;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;

import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@NullMarked
public class PrincipalService {

  private static final int SELF_SERVICE_MINIMUM_AGE = 18;

  private final PrincipalUsers principalUsers;

  public AuthenticatedPerson getFrom(@Valid Person person, Map<String, String> attributes) {
    if (PersonalCode.getAge(person.getPersonalCode()) < SELF_SERVICE_MINIMUM_AGE) {
      log.info("Blocked self-authentication for minor: personalCode={}", person.getPersonalCode());
      throw new MinorCannotSelfAuthenticateException(person.getPersonalCode());
    }
    return getFrom(
        person,
        attributes,
        new Role(PERSON, person.getPersonalCode(), formatted(person.getFullName())));
  }

  public AuthenticatedPerson getFrom(
      @Valid Person person, Map<String, String> attributes, Role role) {

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
    return AuthenticatedPerson.builder()
        .personalCode(person.getPersonalCode())
        .firstName(person.getFirstName())
        .lastName(person.getLastName())
        .userId(person.getUserId())
        .attributes(person.getAttributes())
        .role(role)
        .build();
  }
}
