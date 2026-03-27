package ee.tuleva.onboarding.admin;

import ee.tuleva.onboarding.party.PartyId;
import java.math.BigDecimal;
import java.util.UUID;

record AdjustmentRequest(
    String debitAccount,
    String debitPartyCode,
    String debitPartyType,
    String creditAccount,
    String creditPartyCode,
    String creditPartyType,
    BigDecimal amount,
    UUID externalReference,
    String description) {

  PartyId debitParty() {
    return debitPartyCode != null
        ? new PartyId(PartyId.Type.valueOf(debitPartyType), debitPartyCode)
        : null;
  }

  PartyId creditParty() {
    return creditPartyCode != null
        ? new PartyId(PartyId.Type.valueOf(creditPartyType), creditPartyCode)
        : null;
  }
}
