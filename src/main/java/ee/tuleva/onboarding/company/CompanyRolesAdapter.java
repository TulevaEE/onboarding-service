package ee.tuleva.onboarding.company;

import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;

import ee.tuleva.onboarding.auth.role.CompanyRoles;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CompanyRolesAdapter implements CompanyRoles {

  private final CompanyRepository companyRepository;
  private final CompanyPartyRepository companyPartyRepository;

  @Override
  public List<CompanyRole> boardMemberCompanies(String personalCode) {
    var companyIds =
        companyPartyRepository
            .findByPartyCodeAndPartyTypeAndRelationshipType(personalCode, PERSON, BOARD_MEMBER)
            .stream()
            .map(CompanyParty::getCompanyId)
            .toList();
    return companyRepository.findAllById(companyIds).stream()
        .map(
            company ->
                new CompanyRole(company.getId(), company.getRegistryCode(), company.getName()))
        .toList();
  }

  @Override
  public CompanyRole company(String registryCode) {
    return findCompany(registryCode).orElseThrow(() -> new CompanyNotFoundException(registryCode));
  }

  @Override
  public Optional<CompanyRole> findCompany(String registryCode) {
    return companyRepository
        .findByRegistryCode(registryCode)
        .map(
            company ->
                new CompanyRole(company.getId(), company.getRegistryCode(), company.getName()));
  }

  @Override
  public boolean isBoardMember(String personalCode, String registryCode) {
    return companyRepository
        .findByRegistryCode(registryCode)
        .map(
            company ->
                companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
                    personalCode, PERSON, company.getId(), BOARD_MEMBER))
        .orElse(false);
  }
}
