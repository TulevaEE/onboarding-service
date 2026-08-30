package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.party.PartyId;

public final class LedgerRefs {

  private LedgerRefs() {}

  public static PartyRef from(PartyId partyId) {
    return new PartyRef(PartyType.valueOf(partyId.type().name()), partyId.code());
  }
}
