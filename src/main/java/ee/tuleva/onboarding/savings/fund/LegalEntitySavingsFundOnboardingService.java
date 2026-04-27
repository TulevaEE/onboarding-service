package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;

import ee.tuleva.onboarding.company.BoardMembershipService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegalEntitySavingsFundOnboardingService {

  private final BoardMembershipService boardMembershipService;
  private final SavingsFundOnboardingRepository onboardingRepository;

  public Optional<SavingsFundOnboardingStatus> getOnboardingStatus(
      String personalCode, String registryCode) {
    if (!boardMembershipService.isBoardMember(personalCode, registryCode)) {
      return Optional.empty();
    }
    return onboardingRepository.findStatus(registryCode, LEGAL_ENTITY);
  }

  public boolean isOnboardingCompleted(String personalCode, String registryCode) {
    if (!boardMembershipService.isBoardMember(personalCode, registryCode)) {
      return false;
    }
    return onboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY);
  }
}
