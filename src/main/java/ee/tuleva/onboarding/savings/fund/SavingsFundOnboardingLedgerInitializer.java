package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.ledger.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundOnboardingLedgerInitializer {

  private final LedgerService ledgerService;

  @EventListener
  void onOnboardingCompleted(SavingsFundOnboardingCompletedEvent event) {
    ledgerService.initializeUserAccounts(event.person());
  }
}
