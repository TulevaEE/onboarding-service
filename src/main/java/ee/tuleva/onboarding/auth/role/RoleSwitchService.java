package ee.tuleva.onboarding.auth.role;

import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static java.util.Collections.unmodifiableList;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.TokenService;
import ee.tuleva.onboarding.auth.principal.ActingAs;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.role.RoleController.Role;
import ee.tuleva.onboarding.company.CompanyNotFoundException;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.company.UserCompany;
import ee.tuleva.onboarding.company.UserCompanyRepository;
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
  private final UserCompanyRepository userCompanyRepository;
  private final PrincipalService principalService;
  private final TokenService tokenService;

  public AuthenticationTokens switchRole(AuthenticatedPerson person, ActingAs actingAs) {
    return switch (actingAs) {
      case ActingAs.Person p -> switchToSelf(person, p);
      case ActingAs.Company c -> switchToCompany(person, c);
    };
  }

  public List<Role> getRoles(AuthenticatedPerson person) {
    var roles = new ArrayList<Role>();
    roles.add(new Role(new ActingAs.Person(person.getPersonalCode()), person.getFullName()));

    var companyIds =
        userCompanyRepository
            .findByUserIdAndRelationshipType(person.getUserId(), BOARD_MEMBER)
            .stream()
            .map(UserCompany::getCompanyId)
            .toList();
    companyRepository.findAllById(companyIds).stream()
        .map(
            company -> new Role(new ActingAs.Company(company.getRegistryCode()), company.getName()))
        .forEach(roles::add);

    return unmodifiableList(roles);
  }

  private AuthenticationTokens switchToSelf(AuthenticatedPerson person, ActingAs.Person target) {
    if (!target.code().equals(person.getPersonalCode())) {
      throw new RoleSwitchAccessDeniedException(person.getPersonalCode(), target.code());
    }
    log.info("Role switch to self: personalCode={}", person.getPersonalCode());
    return generateTokens(person, target);
  }

  private AuthenticationTokens switchToCompany(
      AuthenticatedPerson person, ActingAs.Company target) {
    var company =
        companyRepository
            .findByRegistryCode(target.code())
            .orElseThrow(() -> new CompanyNotFoundException(target.code()));

    if (!userCompanyRepository.existsByUserIdAndCompanyIdAndRelationshipType(
        person.getUserId(), company.getId(), BOARD_MEMBER)) {
      throw new RoleSwitchAccessDeniedException(person.getPersonalCode(), target.code());
    }

    log.info(
        "Role switch to company: personalCode={}, registryCode={}",
        person.getPersonalCode(),
        target.code());

    return generateTokens(person, target);
  }

  private AuthenticationTokens generateTokens(AuthenticatedPerson person, ActingAs actingAs) {
    return tokenService.generateTokens(principalService.withActingAs(person, actingAs));
  }
}
