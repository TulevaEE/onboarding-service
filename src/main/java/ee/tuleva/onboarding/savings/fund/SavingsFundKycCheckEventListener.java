package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;

import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SavingsFundKycCheckEventListener {

  private final UserService userService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;

  @EventListener
  @Transactional
  public void onKycCheckPerformed(KycCheckPerformedEvent event) {
    if (event.getPurpose() != PERSONAL_ONBOARDING) {
      return;
    }
    userService
        .findByPersonalCode(event.getPersonalCode())
        .ifPresent(
            user ->
                savingsFundOnboardingService.updateOnboardingStatusIfNeeded(
                    user, event.getKycCheck()));
  }
}
