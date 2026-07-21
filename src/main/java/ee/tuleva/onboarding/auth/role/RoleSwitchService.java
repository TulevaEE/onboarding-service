package ee.tuleva.onboarding.auth.role;

import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static ee.tuleva.onboarding.event.TrackableEventType.ROLE_SWITCH;
import static java.util.Collections.unmodifiableList;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.TokenService;
import ee.tuleva.onboarding.auth.event.RoleSwitchedEvent;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.company.CompanyNotFoundException;
import ee.tuleva.onboarding.company.CompanyParty;
import ee.tuleva.onboarding.company.CompanyPartyRepository;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public class RoleSwitchService {

  private final CompanyRepository companyRepository;
  private final CompanyPartyRepository companyPartyRepository;
  private final PrincipalService principalService;
  private final TokenService tokenService;
  private final ParentChildLinkService parentChildLinkService;
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

    var companyIds =
        companyPartyRepository
            .findByPartyCodeAndPartyTypeAndRelationshipType(
                person.getPersonalCode(), PartyId.Type.PERSON, BOARD_MEMBER)
            .stream()
            .map(CompanyParty::getCompanyId)
            .toList();
    companyRepository.findAllById(companyIds).stream()
        .map(company -> new Role(LEGAL_ENTITY, company.getRegistryCode(), company.getName()))
        .forEach(roles::add);

    parentChildLinkService.findActivelyRepresentedChildCodes(person.getPersonalCode()).stream()
        .map(userService::findByPersonalCode)
        .flatMap(Optional::stream)
        .map(child -> new Role(PERSON, child.getPersonalCode(), child.getFullName()))
        .forEach(roles::add);

    return unmodifiableList(roles);
  }

  public List<PendingOnboardingResponse> getPendingOnboardings(AuthenticatedPerson person) {
    return parentChildLinkService.findPendingChildCodes(person.getPersonalCode()).stream()
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
    if (!parentChildLinkService.isActiveRepresentation(person.getPersonalCode(), command.code())) {
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
    var company =
        companyRepository
            .findByRegistryCode(command.code())
            .orElseThrow(() -> new CompanyNotFoundException(command.code()));

    if (!companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
        person.getPersonalCode(), PartyId.Type.PERSON, company.getId(), BOARD_MEMBER)) {
      throw new RoleSwitchAccessDeniedException(person.getPersonalCode(), command.code());
    }

    log.info(
        "Role switch to company: personalCode={}, registryCode={}",
        person.getPersonalCode(),
        command.code());

    return switchTo(person, new Role(LEGAL_ENTITY, command.code(), company.getName()));
  }

  private AuthenticationTokens switchTo(AuthenticatedPerson person, Role role) {
    AuthenticatedPerson switchedPerson = principalService.withRole(person, role);
    AuthenticationTokens tokens = tokenService.generateTokens(switchedPerson);
    applicationEventPublisher.publishEvent(new RoleSwitchedEvent(switchedPerson));
    applicationEventPublisher.publishEvent(
        new TrackableEvent(
            person, ROLE_SWITCH, Map.of("roleType", role.type().name(), "code", role.code())));
    return tokens;
  }
}
