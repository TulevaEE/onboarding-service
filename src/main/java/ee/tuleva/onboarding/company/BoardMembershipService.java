package ee.tuleva.onboarding.company;

import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BoardMembershipService {

  private final CompanyRepository companyRepository;
  private final CompanyPartyRepository companyPartyRepository;

  // Returns false rather than throwing when the company is unknown,
  // to avoid exposing whether a company exists.
  // TODO: query Äriregister to validate board membership in real time,
  // rather than relying solely on locally stored company_party records.
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
