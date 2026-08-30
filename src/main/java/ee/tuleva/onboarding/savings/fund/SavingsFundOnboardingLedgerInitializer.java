package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.PartyRef;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundOnboardingLedgerInitializer {

  private final LedgerService ledgerService;

  @EventListener
  void onOnboardingCompleted(SavingsFundOnboardingCompletedEvent event) {
    var party = new PartyRef(PartyType.PERSON, event.person().getPersonalCode());
    ledgerService.initializeAccounts(party);
  }
}
