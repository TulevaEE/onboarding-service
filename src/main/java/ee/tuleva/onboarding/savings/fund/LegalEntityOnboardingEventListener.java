package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.REJECTED;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckPerformedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class LegalEntityOnboardingEventListener {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;

  @EventListener
  @Transactional
  public void onKybCheckPerformed(KybCheckPerformedEvent event) {
    var registryCode = event.getCompany().registryCode().value();
    var status = allChecksPassed(event) ? COMPLETED : REJECTED;
    savingsFundOnboardingRepository.saveOnboardingStatus(registryCode, status);
  }

  private boolean allChecksPassed(KybCheckPerformedEvent event) {
    return event.getChecks().stream().allMatch(KybCheck::success);
  }
}
