package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.party.PartyId;

public final class LedgerRefs {

  private LedgerRefs() {}

  public static PartyRef from(PartyId partyId) {
    return new PartyRef(partyType(partyId.type()), partyId.code());
  }

  public static PartyType partyType(PartyId.Type type) {
    return switch (type) {
      case PERSON -> PartyType.PERSON;
      case LEGAL_ENTITY -> PartyType.LEGAL_ENTITY;
    };
  }

  public static PartyType partyType(RoleType roleType) {
    return switch (roleType) {
      case PERSON -> PartyType.PERSON;
      case LEGAL_ENTITY -> PartyType.LEGAL_ENTITY;
    };
  }
}
