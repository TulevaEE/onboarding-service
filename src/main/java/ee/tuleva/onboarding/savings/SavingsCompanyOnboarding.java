package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;

import ee.tuleva.onboarding.kyb.CompanyOnboarding;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsCompanyOnboarding implements CompanyOnboarding {

  private final SavingsFundOnboardingService onboardingService;

  @Override
  public Optional<State> findState(String registryCode) {
    return onboardingService
        .findStatus(registryCode, LEGAL_ENTITY)
        .map(status -> State.valueOf(status.name()));
  }
}
