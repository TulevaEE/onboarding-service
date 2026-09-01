package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;

import ee.tuleva.onboarding.party.ChildSavingsOnboarding;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ChildSavingsOnboardingAdapter implements ChildSavingsOnboarding {

  private final SavingsFundOnboardingService savingsFundOnboardingService;

  @Override
  public boolean isCompleted(String personalCode) {
    return savingsFundOnboardingService.isOnboardingCompleted(new PartyId(PERSON, personalCode));
  }

  @Override
  public void seedIfAbsent(String personalCode) {
    savingsFundOnboardingService.seedPersonOnboardingIfAbsent(personalCode);
  }
}
