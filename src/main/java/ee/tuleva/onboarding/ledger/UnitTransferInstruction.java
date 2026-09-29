package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;
import java.util.UUID;

public record UnitTransferInstruction(
    PartyRef from,
    PartyRef to,
    BigDecimal fundUnits,
    BigDecimal recipientAcquisitionCost,
    UUID externalReference) {}
