package ee.tuleva.onboarding.admin.ledger;

import ee.tuleva.onboarding.ledger.LedgerParty;
import ee.tuleva.onboarding.ledger.PartyReclassification;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.UserAccount;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record ReclassificationRequest(
    String account,
    String debitPartyCode,
    String debitPartyType,
    String creditPartyCode,
    String creditPartyType,
    BigDecimal amount,
    @Nullable UUID externalReference,
    @Nullable UUID correctedTransactionId,
    String description) {

  PartyReclassification toReclassification() {
    return new PartyReclassification(
        UserAccount.valueOf(account),
        debitParty(),
        creditParty(),
        amount,
        externalReference,
        correctedTransactionId,
        description);
  }

  PartyRef debitParty() {
    return new PartyRef(LedgerParty.PartyType.valueOf(debitPartyType), debitPartyCode);
  }

  PartyRef creditParty() {
    return new PartyRef(LedgerParty.PartyType.valueOf(creditPartyType), creditPartyCode);
  }
}
