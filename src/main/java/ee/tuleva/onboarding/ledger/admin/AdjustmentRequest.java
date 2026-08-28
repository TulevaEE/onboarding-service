package ee.tuleva.onboarding.ledger.admin;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.party.PartyId;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record AdjustmentRequest(
    String debitAccount,
    @Nullable String debitPartyCode,
    @Nullable String debitPartyType,
    String creditAccount,
    @Nullable String creditPartyCode,
    @Nullable String creditPartyType,
    BigDecimal amount,
    @Nullable UUID externalReference,
    String description) {

  @Nullable PartyId debitParty() {
    if (debitPartyCode == null) {
      return null;
    }
    return new PartyId(
        PartyId.Type.valueOf(requireNonNull(debitPartyType, "Missing debitPartyType")),
        debitPartyCode);
  }

  @Nullable PartyId creditParty() {
    if (creditPartyCode == null) {
      return null;
    }
    return new PartyId(
        PartyId.Type.valueOf(requireNonNull(creditPartyType, "Missing creditPartyType")),
        creditPartyCode);
  }
}
