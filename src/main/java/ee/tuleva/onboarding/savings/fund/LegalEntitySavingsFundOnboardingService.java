package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;

import ee.tuleva.onboarding.company.CompanyPartyRepository;
import ee.tuleva.onboarding.company.CompanyRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegalEntitySavingsFundOnboardingService {

  private final CompanyRepository companyRepository;
  private final CompanyPartyRepository companyPartyRepository;
  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;

  public Optional<SavingsFundOnboardingStatus> getOnboardingStatus(
      String personalCode, String registryCode) {
    if (!isBoardMember(personalCode, registryCode)) {
      return Optional.empty();
    }
    return savingsFundOnboardingRepository.findStatusByPersonalCode(registryCode);
  }

  public boolean isOnboardingCompleted(String personalCode, String registryCode) {
    if (!isBoardMember(personalCode, registryCode)) {
      return false;
    }
    return savingsFundOnboardingRepository.isOnboardingCompleted(registryCode);
  }

  // Returns false rather than throwing when the user is not a board member,
  // to avoid exposing whether a company exists or has completed onboarding.
  // TODO: query Äriregister to validate board membership in real time,
  // rather than relying solely on locally stored company_party records.
  private boolean isBoardMember(String personalCode, String registryCode) {
    return companyRepository
        .findByRegistryCode(registryCode)
        .map(
            company ->
                companyPartyRepository.existsByPartyCodeAndPartyTypeAndCompanyIdAndRelationshipType(
                    personalCode,
                    ee.tuleva.onboarding.company.PartyType.PERSON,
                    company.getId(),
                    BOARD_MEMBER))
        .orElse(false);
  }
}
