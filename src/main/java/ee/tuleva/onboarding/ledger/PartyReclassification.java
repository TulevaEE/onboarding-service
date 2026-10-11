package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PartyReclassification(
    UserAccount account,
    PartyRef debitParty,
    PartyRef creditParty,
    BigDecimal amount,
    @Nullable UUID externalReference,
    @Nullable UUID correctedTransactionId,
    String description) {}
