package ee.tuleva.onboarding.auth.role;

import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static java.util.Collections.unmodifiableList;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.TokenService;
import ee.tuleva.onboarding.auth.event.RoleSwitchedEvent;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
class RoleSwitchService {

  private final CompanyRoles companyRoles;
  private final PrincipalService principalService;
  private final TokenService tokenService;
  private final ChildRepresentations childRepresentations;
  private final UserService userService;
  private final ApplicationEventPublisher applicationEventPublisher;

  public AuthenticationTokens switchRole(AuthenticatedPerson person, SwitchRoleCommand command) {
    return switch (command.type()) {
      case PERSON -> switchToPerson(person, command);
      case LEGAL_ENTITY -> switchToCompany(person, command);
    };
  }

  public List<Role> getRoles(AuthenticatedPerson person) {
    var roles = new ArrayList<Role>();
    roles.add(new Role(PERSON, person.getPersonalCode(), person.getFullName()));

    companyRoles.boardMemberCompanies(person.getPersonalCode()).stream()
        .map(company -> new Role(LEGAL_ENTITY, company.registryCode(), company.name()))
        .forEach(roles::add);

    childRepresentations.findActivelyRepresentedChildCodes(person.getPersonalCode()).stream()
        .map(userService::findByPersonalCode)
        .flatMap(Optional::stream)
        .map(child -> new Role(PERSON, child.getPersonalCode(), child.getFullName()))
        .forEach(roles::add);

    return unmodifiableList(roles);
  }

  public List<PendingOnboardingResponse> getPendingOnboardings(AuthenticatedPerson person) {
    return childRepresentations.findPendingChildCodes(person.getPersonalCode()).stream()
        .map(userService::findByPersonalCode)
        .flatMap(Optional::stream)
        .map(
            child ->
                new PendingOnboardingResponse(PERSON, child.getPersonalCode(), child.getFullName()))
        .toList();
  }

  private AuthenticationTokens switchToPerson(
      AuthenticatedPerson person, SwitchRoleCommand command) {
    if (command.code().equals(person.getPersonalCode())) {
      log.info("Role switch to self: personalCode={}", person.getPersonalCode());
      return switchTo(person, new Role(PERSON, command.code(), person.getFullName()));
    }
    return switchToRepresentedChild(person, command);
  }

  private AuthenticationTokens switchToRepresentedChild(
      AuthenticatedPerson person, SwitchRoleCommand command) {
    if (!childRepresentations.isActiveRepresentation(person.getPersonalCode(), command.code())) {
      throw new RoleSwitchAccessDeniedException(person.getPersonalCode(), command.code());
    }
    User child =
        userService
            .findByPersonalCode(command.code())
            .orElseThrow(
                () ->
                    new RoleSwitchAccessDeniedException(person.getPersonalCode(), command.code()));
    log.info(
        "Role switch to represented child: personalCode={}, childCode={}",
        person.getPersonalCode(),
        command.code());
    return switchTo(person, new Role(PERSON, command.code(), child.getFullName()));
  }

  private AuthenticationTokens switchToCompany(
      AuthenticatedPerson person, SwitchRoleCommand command) {
    var company = companyRoles.company(command.code());

    if (!companyRoles.isBoardMember(person.getPersonalCode(), command.code())) {
      throw new RoleSwitchAccessDeniedException(person.getPersonalCode(), command.code());
    }

    log.info(
        "Role switch to company: personalCode={}, registryCode={}",
        person.getPersonalCode(),
        command.code());

    return switchTo(person, new Role(LEGAL_ENTITY, command.code(), company.name()));
  }

  private AuthenticationTokens switchTo(AuthenticatedPerson person, Role role) {
    AuthenticatedPerson switchedPerson = principalService.withRole(person, role);
    AuthenticationTokens tokens = tokenService.generateTokens(switchedPerson);
    applicationEventPublisher.publishEvent(new RoleSwitchedEvent(person, switchedPerson));
    return tokens;
  }
}
