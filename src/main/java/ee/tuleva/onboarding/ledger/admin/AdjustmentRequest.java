package ee.tuleva.onboarding.ledger.admin;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.ledger.LedgerParty;
import ee.tuleva.onboarding.ledger.PartyRef;
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

  @Nullable PartyRef debitParty() {
    if (debitPartyCode == null) {
      return null;
    }
    return new PartyRef(
        LedgerParty.PartyType.valueOf(requireNonNull(debitPartyType, "Missing debitPartyType")),
        debitPartyCode);
  }

  @Nullable PartyRef creditParty() {
    if (creditPartyCode == null) {
      return null;
    }
    return new PartyRef(
        LedgerParty.PartyType.valueOf(requireNonNull(creditPartyType, "Missing creditPartyType")),
        creditPartyCode);
  }
}
