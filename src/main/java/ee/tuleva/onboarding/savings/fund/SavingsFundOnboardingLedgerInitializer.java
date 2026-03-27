package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.party.PartyId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundOnboardingLedgerInitializer {

  private final LedgerService ledgerService;

  @EventListener
  void onOnboardingCompleted(SavingsFundOnboardingCompletedEvent event) {
    var party = new PartyId(PartyId.Type.PERSON, event.person().getPersonalCode());
    ledgerService.initializeAccounts(party);
  }
}
