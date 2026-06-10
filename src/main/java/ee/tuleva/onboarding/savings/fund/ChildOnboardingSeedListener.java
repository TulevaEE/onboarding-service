package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.party.ParentChildLinkRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class ChildOnboardingSeedListener {

  private final SavingsFundOnboardingService savingsFundOnboardingService;

  @EventListener
  @Transactional
  void onParentChildLinkRegistered(ParentChildLinkRegisteredEvent event) {
    savingsFundOnboardingService.seedPersonOnboardingIfAbsent(event.childPersonalCode());
  }
}
