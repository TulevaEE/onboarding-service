package ee.tuleva.onboarding.auth.principal;

import static ee.tuleva.onboarding.auth.principal.Names.formatted;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;

import ee.tuleva.onboarding.auth.role.Role;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@NullMarked
public class PrincipalService {

  private final PrincipalUsers principalUsers;

  @Value("${minor-self-service.enabled}")
  private final boolean minorSelfServiceEnabled;

  private final Clock clock;

  public AuthenticatedPerson getFrom(@Valid Person person, Map<String, String> attributes) {
    var ownRole = new Role(PERSON, person.getPersonalCode(), formatted(person.getFullName()));
    if (refusesSelfServiceTo(person, ownRole)) {
      log.info("Blocked self-authentication for minor: personalCode={}", person.getPersonalCode());
      throw new MinorCannotSelfAuthenticateException(person.getPersonalCode());
    }
    return getFrom(person, attributes, ownRole);
  }

  public boolean refusesSelfServiceTo(Person person, @Nullable Role role) {
    return !minorSelfServiceEnabled
        && AuthenticatedPerson.builder()
            .personalCode(person.getPersonalCode())
            .role(role)
            .build()
            .isMinorActingAsSelf(LocalDate.now(clock));
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
