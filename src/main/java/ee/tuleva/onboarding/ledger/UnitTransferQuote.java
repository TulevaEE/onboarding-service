package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;

/** What a transfer would do, answered without writing anything. */
public record UnitTransferQuote(
    BigDecimal fundUnits, BigDecimal giverUnitsAfter, BigDecimal receiverUnitsAfter) {}
