package ee.tuleva.onboarding.auth.role;

import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static java.util.Collections.unmodifiableList;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.TokenService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.company.CompanyNotFoundException;
import ee.tuleva.onboarding.company.CompanyParty;
import ee.tuleva.onboarding.company.CompanyPartyRepository;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.company.PartyType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleSwitchService {

  private final CompanyRepository companyRepository;
  private final CompanyPartyRepository companyPartyRepository;
  private final PrincipalService principalService;
  private final TokenService tokenService;

  public AuthenticationTokens switchRole(AuthenticatedPerson person, SwitchRoleCommand command) {
    return switch (command.type()) {
      case PERSON -> switchToSelf(person, command);
      case LEGAL_ENTITY -> switchToCompany(person, command);
    };
  }

  public List<Role> getRoles(AuthenticatedPerson person) {
    var roles = new ArrayList<Role>();
    roles.add(new Role(PERSON, person.getPersonalCode(), person.getFullName()));

    var companyIds =
        companyPartyRepository
            .findByPartyCodeAndPartyTypeAndRelationshipType(
                person.getPersonalCode(), PartyType.PERSON, BOARD_MEMBER)
            .stream()
            .map(CompanyParty::getCompanyId)
            .toList();
    companyRepository.findAllById(companyIds).stream()
        .map(company -> new Role(LEGAL_ENTITY, company.getRegistryCode(), company.getName()))
        .forEach(roles::add);

    return unmodifiableList(roles);
  }

  private AuthenticationTokens switchToSelf(AuthenticatedPerson person, SwitchRoleCommand command) {
    if (!command.code().equals(person.getPersonalCode())) {
      throw new RoleSwitchAccessDeniedException(person.getPersonalCode(), command.code());
    }
    log.info("Role switch to self: personalCode={}", person.getPersonalCode());
    var role = new Role(PERSON, command.code(), person.getFullName());
    return generateTokens(person, role);
  }

  private AuthenticationTokens switchToCompany(
      AuthenticatedPerson person, SwitchRoleCommand command) {
    var company =
        companyRepository
            .findByRegistryCode(command.code())
            .orElseThrow(() -> new CompanyNotFoundException(command.code()));

    if (!companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
        person.getPersonalCode(), PartyType.PERSON, company.getId(), BOARD_MEMBER)) {
      throw new RoleSwitchAccessDeniedException(person.getPersonalCode(), command.code());
    }

    log.info(
        "Role switch to company: personalCode={}, registryCode={}",
        person.getPersonalCode(),
        command.code());

    var role = new Role(LEGAL_ENTITY, command.code(), company.getName());
    return generateTokens(person, role);
  }

  private AuthenticationTokens generateTokens(AuthenticatedPerson person, Role role) {
    return tokenService.generateTokens(principalService.withRole(person, role));
  }
}
